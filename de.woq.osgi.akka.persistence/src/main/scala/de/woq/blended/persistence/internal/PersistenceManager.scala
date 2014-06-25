/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.blended.persistence.internal

import akka.actor._
import de.woq.blended.akka.protocol._
import de.woq.blended.akka.{BundleName, OSGIActor}
import de.woq.blended.container.context.ContainerContext
import org.osgi.framework.BundleContext
import akka.event.LoggingReceive
import akka.pattern._
import de.woq.blended.persistence.protocol._

trait PersistenceProvider {
  val backend : PersistenceBackend
}

object PersistenceManager {

  def apply(impl: PersistenceBackend)(implicit osgiContext : BundleContext) =
    new PersistenceManager() with OSGIActor with PersistenceBundleName with PersistenceProvider {
      override val backend = impl
    }
}

class PersistenceManager()(implicit osgiContext : BundleContext)
  extends Actor with ActorLogging { this : OSGIActor with BundleName with PersistenceProvider =>

  implicit val logging = context.system.log

  private var factories : List[ActorRef] = List.empty
  private var requests : List[(ActorRef, Any)] = List.empty

  def initializing = LoggingReceive {
    case InitializeBundle(_) => {
      val cfg = getActorConfig(bundleSymbolicName)
      val d = invokeService(classOf[ContainerContext]) { ctx => ctx.getContainerDirectory }
      (for {
        config <- cfg
        dir <- d
      } yield (config, dir)) pipeTo(self)
    }
    case (ConfigLocatorResponse(id, config), ServiceResult(Some(dir : String))) if id == bundleSymbolicName => {
      backend.initBackend(dir, config)
      requests.reverse.foreach{ case (s, m) => self.tell(m, s) }
      context.become(working)
    }
    case r => requests = (sender, r) :: requests
  }

  def working = LoggingReceive {
    case RegisterDataFactory(factory: ActorRef) => {
      if (!factories.contains(factory)) {
        factories = factory :: factories
        context.watch(factory)
      }
    }
    case Terminated(factory) => {
      factories = factories.filter(_ != factory)
    }
    case StoreObject(dataObject) => {
      backend.store(dataObject)
      sender ! QueryResult(List(dataObject))
    }
    case FindObjectByID(uuid, objectType) => {
      backend.get(uuid, objectType) match {
        case None => sender ! QueryResult(List.empty)
        case Some(props) => {
          log.debug(s"Asking [${factories.size}] factories to create the dataObject.")
          factories.foreach { f => f.forward(CreateObjectFromProperties(props)) }
        }
      }
    }
  }

  def receive = initializing

  override def postStop() {
    backend.shutdownBackend()
  }
}