package blended.websocket

import blended.websocket.json.PrickleProtocol._
import prickle._

import scala.util.Try

object JsonHelper {

  def decode[T](s : String)(implicit up: Unpickler[T]) : Try[T] = Try {
    Unpickle[T].fromString(s).get
  }

  def encode[T](obj : T)(implicit p : Pickler[T]) : String = Pickle.intoString(obj)
}

// scalastyle:off magic.number
case class WsContext(
  namespace : String,
  name : String,
  status : Int = 200,
  statusMsg : Option[String] = None
)
// scalastyle:on magic.number

object WsMessageEncoded {

  def fromContext(context : WsContext) : String = fromObject(context, ())

  def fromObject[T](context: WsContext, t : T)(implicit p:  Pickler[T]) : String = {
    Pickle.intoString(WsMessageEncoded(
      context = context, content = Pickle.intoString(t)
    ))
  }
}

case class WsMessageEncoded(
  context : WsContext,
  // The payload - either command parameters sent by the client or the message
  // sent to a client
  content : String
)