/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.mgmt.agent.internal

import akka.actor.Cancellable
import akka.event.LoggingReceive
import blended.akka.{ OSGIActor, OSGIActorConfig }
import blended.container.context.ContainerIdentifierService
import blended.container.registry.protocol._
import spray.client.pipelining._
import spray.http.HttpRequest
import spray.httpx.SprayJsonSupport
import scala.collection.JavaConverters._
import akka.pattern.pipe
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.Props

object MgmtReporter {
  def props(cfg: OSGIActorConfig): Props = Props(new MgmtReporter(cfg))
}

class MgmtReporter(cfg: OSGIActorConfig) extends OSGIActor(cfg) with SprayJsonSupport {

  implicit private[this] val eCtxt = context.system.dispatcher
  private[this] var ticker: Option[Cancellable] = None

  case object Tick

  override def preStart(): Unit = {
    ticker = Some(context.system.scheduler.schedule(100.milliseconds, 60.seconds, self, Tick))
    super.preStart()
  }

  override def postStop(): Unit = {
    ticker.foreach(_.cancel())
    ticker = None
    super.postStop()
  }

  def receive: Receive = LoggingReceive {

    case Tick =>
      withService[ContainerIdentifierService, Unit] {
        case Some(idSvc) =>
          val info = ContainerInfo(idSvc.getUUID(), idSvc.getProperties().asScala.toMap)
          log info s"Performing report [${info.toString}]."
          val pipeline: HttpRequest => Future[ContainerRegistryResponseOK] = {
            sendReceive ~> unmarshal[ContainerRegistryResponseOK]
          }
          pipeline { Post("http://localhost:8181/wayofquality/container", info) }.mapTo[ContainerRegistryResponseOK].pipeTo(self)
        case None =>
      }

    case response: ContainerRegistryResponseOK => log.info("Reported [{}] to management node", response.id)
  }

}
