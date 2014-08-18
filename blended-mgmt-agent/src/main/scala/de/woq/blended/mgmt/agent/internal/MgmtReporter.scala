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

package de.woq.blended.mgmt.agent.internal


import akka.actor.{Actor, ActorLogging, Cancellable}
import de.woq.blended.akka.protocol._
import de.woq.blended.akka.{OSGIActor, BundleName}
import de.woq.blended.container.id.ContainerIdentifierService
import de.woq.blended.container.registry.protocol._
import org.osgi.framework.BundleContext
import akka.event.LoggingReceive
import akka.pattern.pipe
import scala.concurrent.duration._
import spray.httpx.SprayJsonSupport
import scala.concurrent.Future
import spray.client.pipelining._
import spray.http.HttpRequest
import scala.collection.JavaConversions._

object MgmtReporter {
  def apply()(implicit bundleContext: BundleContext) = new MgmtReporter with OSGIActor with MgmtAgentBundleName
}

class MgmtReporter extends Actor with ActorLogging with SprayJsonSupport { this : OSGIActor with BundleName =>

  case object Tick

  private [MgmtReporter] var ticker : Option[Cancellable] = None

  def initializing = LoggingReceive {
    case InitializeBundle(bundleContext) => {
      log info "Initializing Management Reporter"
      ticker = Some(context.system.scheduler.schedule(100.milliseconds, 60.seconds, self, Tick))
      context.become(working(bundleContext))
    }
  }

  def working(implicit osgiContext: BundleContext) = LoggingReceive {

    case Tick => {

      invokeService[ContainerIdentifierService, ContainerInfo](classOf[ContainerIdentifierService]) { idSvc =>
        new ContainerInfo(idSvc.getUUID, idSvc.getProperties.toMap)
      } pipeTo(self)
    }

    case ServiceResult(Some(info : ContainerInfo))  => {
      log info s"Performing report [${info.toString}]."

      val pipeline :  HttpRequest => Future[ContainerRegistryResponseOK] = {
        sendReceive ~> unmarshal[ContainerRegistryResponseOK]
      }

      (pipeline{ Post("http://localhost:8181/woq/container", info) }).mapTo[ContainerRegistryResponseOK].pipeTo(self)
    }

    case response : ContainerRegistryResponseOK => log info(s"Reported [${response.id}] to management node")
  }

  def receive = initializing

  override def postStop() {
    ticker.foreach(_.cancel())
  }
}
