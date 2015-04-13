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

package de.wayofquality.blended.persistence.internal

import akka.actor.{ActorRef, Props}
import akka.testkit.TestActorRef
import akka.util.Timeout
import de.wayofquality.blended.akka.internal.OSGIFacade
import de.wayofquality.blended.akka.protocol._
import de.wayofquality.blended.akka.{BlendedAkkaConstants, OSGIActor}
import de.wayofquality.blended.persistence.internal.person.{Person, PersonCreator}
import de.wayofquality.blended.persistence.protocol.{QueryResult, StoreObject, _}
import de.wayofquality.blended.testsupport.TestActorSys
import org.osgi.framework.BundleActivator
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class PersistenceManagerSpec
  extends TestActorSys
  with WordSpecLike
  with Matchers
  with PersistenceBundleName
  with TestSetup
  with MockitoSugar
  with BeforeAndAfterAll {

  var facade : ActorRef = _
  var activator : BundleActivator = _
  var dataCreator : ActorRef = _

  implicit val timeout = Timeout(3.seconds)
  implicit val ctxt = system.dispatcher

  override protected def beforeAll() : Unit = {
    facade = TestActorRef(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)

    dataCreator = system.actorOf(Props(new DataObjectCreator(new PersonCreator()) with OSGIActor), "person")
    system.eventStream.subscribe(dataCreator, classOf[BundleActorInitialized])
    system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])

    activator = new PersistenceActivator
    activator.start(osgiContext)
  }

  override protected def afterAll() : Unit = {
    activator.stop(osgiContext)
  }

  "The PersistenceManager" should {

    "Initialize correctly" in {
      expectMsgType[BundleActorInitialized](30.seconds)
    }

    "Store a data object correctly" in {
      val info = new Person(firstName = "Andreas", name = "Gies")
      system.actorSelection(s"/user/$bundleSymbolicName").resolveOne().map( _ ! StoreObject(info) )

      fishForMessage(10.seconds) {
        case QueryResult(List(info)) => true
        case _ => false
      }
    }

    "Retrieve a data object by its uuid" in {
      val info = new Person(firstName = "Andreas", name = "Gies")
      system.actorSelection(s"/user/$bundleSymbolicName").resolveOne().map( _ ! StoreObject(info) )

      fishForMessage(10.seconds) {
        case QueryResult(List(info)) => true
        case _ => false
      }

      // TODO : Should be a get object message ???
      system.actorSelection(s"/user/$bundleSymbolicName").resolveOne().map( _ ! StoreObject(info) )

      fishForMessage(10.seconds) {
        case QueryResult(List(person)) => person == info
        case _ => {
          false
        }
      }
    }
  }
}