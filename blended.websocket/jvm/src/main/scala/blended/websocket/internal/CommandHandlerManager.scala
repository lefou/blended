package blended.websocket.internal

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{NotUsed, actor}
import blended.security.login.api.Token
import blended.util.logging.Logger
import blended.websocket.{ClientInfo, WebSocketCommandHandler, WsUnitMessage}

trait CommandHandlerManager {
  /**
    * This method will be called every time a client has successfully authenticated.
    * The token will contain all the user info including the user id and the permissions
    * for the user. If required, the token will be passed to the command handlers so that
    * client specific permissions can be evaluated.
    * Essentially it will create a flow from Strings to WsUnitMessages. This flow will then
    * try to decode the incoming command and produce a Websockets message carrying a result.
    * @param t : The token with the client specific id and permissions.
    */
  def newClient(t : Token) : Flow[String, WsUnitMessage, NotUsed]
}

object CommandHandlerManager {

  case class AddCommandHandler(handler: WebSocketCommandHandler[_])
  case class RemoveCommandHandler(handler: WebSocketCommandHandler[_])
  case class NewClient(t : Token, clientActor : ActorRef)
  case class ClientClosed(t : Token)
  case class ReceivedMessage(t: Token, s : String)
  case class WsClientUpdate(
    msg : TextMessage.Strict,
    client : ClientInfo
  )

  /**
    * Create an empty command handler within an Actor system.
    */
  def create(system: ActorSystem): CommandHandlerManager = {
    // This creates one actor which will dispatch all incoming client
    // messages and dispatch them accordingly
    val cmdHandler = system.actorOf(Props[CommandHandlerActor])

    // for each new client we will create a flow which will consume Strings and emit
    // WSMessageEnvelopes
    token: Token => {
      val in = Flow[String]
        .map(s => ReceivedMessage(token, s))
        .to(Sink.actorRef[ReceivedMessage](cmdHandler, ClientClosed(token)))

      // This materializes a new actor for the given client. All messages sent to this actor
      // will be sent to the client via Web Sockets
      // The new client will be registered with the DispatcherActor, which will then watch this
      // actor and dispatch events to the client as long as it is active.
      val out = Source.actorRef[WsUnitMessage](1, OverflowStrategy.fail)
        .mapMaterializedValue { c => cmdHandler ! NewClient(token, c) }

      Flow.fromSinkAndSourceCoupled(in, out)
    }
  }

  /**
    * The central Web sockets command handler.
    *
    * The central command handler will keep track of all registered specialized
    * command handlers, so that it can dispatch any inbound WsMessage to the
    * relevant command handler. It will also keep track of the connected clients,
    * as the command handlers will eventually send WsMessages to a particular client.
    */
  private class CommandHandlerActor extends Actor {

    private val log : Logger = Logger[CommandHandlerActor]

    // We start with no handlers and no clients
    override def preStart(): Unit = {
      context.become(handling(CommandHandlerState()))
    }

    override def receive: Receive = Actor.emptyBehavior

    private def handling(state : CommandHandlerState) : Receive = {
      // Manage Ws Command Handler
      case AddCommandHandler(h) =>
        context.become(handling(state.addHandler(h)))
      case RemoveCommandHandler(h) =>
        context.become(handling(state.removeHandler(h)))

      // Manage client connects / disconnects
      case NewClient(info, clientActor) =>
        context.watch(clientActor)
        context.become(handling(state.addClient(ClientInfo(info, clientActor))))
      case ClientClosed(t) =>
        state.clients.get(t.id).foreach { ci =>
          ci.clientActor ! actor.Status.Success(Unit)
        }
        context.become(handling(state.removeClient(t)))
      case Terminated(ca) =>
        state.clients.values.find(_.clientActor == ca).foreach { ci =>
          context.become(handling(state.removeClient(ci.t)))
        }

      // Forward an emitted message from a command handler to the connected client
      case u : WsClientUpdate =>
        state.clients.values.find(_.t.id == u.client.t.id).foreach{ ci =>
          ci.clientActor ! u.msg
        }

      case rm : ReceivedMessage =>
        log.info(s"Handling message [${rm.s}] for client [${rm.t.id}]")

    }
  }
}