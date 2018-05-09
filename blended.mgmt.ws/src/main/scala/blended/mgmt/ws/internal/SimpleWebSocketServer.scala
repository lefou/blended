package blended.mgmt.ws.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Flow
import blended.updater.config.ContainerInfo
import blended.updater.config.json.PrickleProtocol._
import prickle.Pickle

import scala.util.Failure

class SimpleWebSocketServer(system: ActorSystem) {

  private[this] val log = org.log4s.getLogger
  private[this] implicit val eCtxt = system.dispatcher
  private[this] val dispatcher = Dispatcher.create(system)

  def route : Route = routeImpl

  private[this] lazy val routeImpl : Route = path("timer") {
    parameter('name) {
      name =>
        log.info(s"Starting Web Socket message handler ... [$name]")
        handleWebSocketMessages(dispatcherFlow(name))
    }
  }

  private[this] def dispatcherFlow(name: String) : Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) => msg
      }
      .via(dispatcher.newClient(name))
      .map {

        case ReceivedMessage(m) =>
          TextMessage.Strict(m)

        case NewData(data) => data match {
          case ctInfo : ContainerInfo =>
            val json : String = Pickle.intoString(ctInfo)
            TextMessage.Strict(json)

          case _ => TextMessage.Strict("")
        }

        case o =>
          TextMessage.Strict(o.toString())
      }
      .via(reportErrorsFlow)
  }

  def reportErrorsFlow[T]: Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          println(s"WS stream failed with $cause")
        case _ => // ignore regular completion
      })
}