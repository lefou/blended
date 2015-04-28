/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.wayofquality.blended.akka

import akka.actor._
import akka.event.LoggingReceive
import akka.testkit.{TestProbe, TestActorRef, TestLatch}
import com.typesafe.config.Config
import de.wayofquality.blended.testsupport.TestActorSys
import de.wayofquality.blended.akka.protocol._
import org.osgi.framework.BundleContext
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.{Future, Await}
import scala.util.{Success, Try}

object OSGIActorDummyPublisher {
  def apply()(implicit bundleContext: BundleContext) = new OSGIActorDummyPublisher(bundleContext) with OSGIActor
}

class OSGIActorDummyPublisher(ctxt: BundleContext) extends OSGIActor with ProductionEventSource with BundleName {

  override protected def bundleContext: BundleContext = ctxt

  override def bundleSymbolicName = "publisher"

  def receive = LoggingReceive { eventSourceReceive }
}

object OSGIDummyListener {
  def apply()(implicit bundleContext : BundleContext) = new OSGIDummyListener(bundleContext) with OSGIEventSourceListener
}

class OSGIDummyListener(ctxt: BundleContext) extends InitializingActor[BundleActorState] with ActorLogging with BundleName { this : OSGIEventSourceListener =>

  override protected def bundleContext: BundleContext = ctxt

  var publisherName : Option[String] = None

  implicit val actorSys = context.system
  val latch = TestLatch(1)

  override def createState(cfg: Config, bundleContext: BundleContext): BundleActorState = 
    BundleActorState(cfg, bundleContext)

  def receive = initializing

  override def bundleSymbolicName = "listener"

  override def initialize(state: BundleActorState) : Try[Initialized] = {
    publisherName = Some(state.config.getString("publisher"))
    setupListener(publisherName.get)
    Success(Initialized(state))
  }

  def working(state: BundleActorState) = testing orElse eventListenerReceive(publisherName.get)

  def testing : Receive = {
    case "Andreas" => latch.countDown()
  }
}

class OSGIEventSourceListenerSpec extends WordSpec with Matchers {

  "The OSGI Event source listener" should {

    "subscribe to a publisher bundle if it already exists in the actor system" in new TestActorSys with TestSetup with MockitoSugar {

      import scala.concurrent.duration._

      system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])

      val publisher = TestActorRef(Props(OSGIActorDummyPublisher()), "publisher")
      val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

      publisher ! InitializeBundle(osgiContext)
      listener ! InitializeBundle(osgiContext)

      // We need to wait for the Actor bundle to finish it's initialization
      fishForMessage() {
        case BundleActorInitialized(s) if s == "listener" => true
        case _ => false
      }

      //TODO: This is a test only hack to give the listener some time to finish its registration
      Thread.sleep(100)

      publisher ! SendEvent("Andreas")

      val latch = listener.underlyingActor.asInstanceOf[OSGIDummyListener].latch
      Await.ready(latch, 1.second)
      latch.isOpen should be (true)
    }
  }

  "start referring to the dlc when the publisher is unavailbale" in new TestActorSys with TestSetup with MockitoSugar {

    val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

    system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])
    listener ! InitializeBundle(osgiContext)

    // We need to wait for the Actor bundle to finish it's initialization
    fishForMessage() {
      case BundleActorInitialized(s) if s == "listener" => true
      case _ => false
    }

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(system.deadLetters)
  }

  "subscribe to the publisher when it becomes available" in new TestActorSys with TestSetup with MockitoSugar {

    import scala.concurrent.duration._

    val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

    system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])
    listener ! InitializeBundle(osgiContext)

    // We need to wait for the Actor bundle to finish it's initialization
    fishForMessage() {
      case BundleActorInitialized(s) if s == "listener" => true
      case _ => false
    }

    val publisher = TestActorRef(Props(OSGIActorDummyPublisher()), "publisher")
    system.eventStream.publish(BundleActorInitialized("publisher"))

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(publisher)

    publisher ! SendEvent("Andreas")
    val latch = listener.underlyingActor.asInstanceOf[OSGIDummyListener].latch
    Await.ready(latch, 1.second)
    latch.isOpen should be (true)
  }

  "fallback to system.dlc when the publisher becomes unavailable" in new TestActorSys with TestSetup with MockitoSugar {

    val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

    system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])
    listener ! InitializeBundle(osgiContext)

    // We need to wait for the Actor bundle to finish it's initialization
    fishForMessage() {
      case BundleActorInitialized(s) if s == "listener" => true
      case _ => false
    }

    val publisher = TestActorRef(Props(OSGIActorDummyPublisher()), "publisher")
    system.eventStream.publish(BundleActorInitialized("publisher"))

    val watcher = new TestProbe(system)
    watcher.watch(publisher)

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(publisher)

    system.stop(publisher)
    watcher.expectMsgType[Terminated]

    listenerReal.publisher should be (system.deadLetters)
  }
}
