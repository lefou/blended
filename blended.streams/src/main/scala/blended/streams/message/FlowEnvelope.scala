package blended.streams.message

import blended.jms.utils.JmsAckSession
import blended.streams.message.FlowMessage.FlowMessageProps
import javax.jms.Message

import scala.beans.BeanProperty
import scala.util.Try

case class AckInfo(
  jmsMessage : Message,
  session : JmsAckSession,
  created : Long = System.currentTimeMillis()
)

object FlowEnvelope {

  def apply(props : FlowMessageProps) : FlowEnvelope = FlowEnvelope(FlowMessage(props))
}

final case class FlowEnvelope(
  @BeanProperty
  flowMessage : FlowMessage,
  @BeanProperty
  exception :Option[Throwable] = None,
  @BeanProperty
  requiresAcknowledge : Boolean = false,
  @BeanProperty
  ackInfo : Option[AckInfo] = None,

  flowContext : Map[String, Any] = Map.empty
) {

  def withHeader(key: String, value: Any, overwrite: Boolean = true) : Try[FlowEnvelope] = Try {
    copy(flowMessage = flowMessage.withHeader(key, value, overwrite).get)
  }

  def removeFromContext(key: String) : FlowEnvelope = copy(flowContext = flowContext.filter(_ != key))
  def setInContext(key: String, o: Any) : FlowEnvelope = copy(flowContext = flowContext.filter(_ != key) + (key -> o))

  def getFromContext[T](key: String) : Try[Option[T]] = Try { flowContext.get(key).map(_.asInstanceOf[T]) }

  def clearException(): FlowEnvelope = copy(exception = None)
  def withException(t: Throwable): FlowEnvelope = copy(exception = Some(t))
  def withRequiresAcknowledge(b: Boolean): FlowEnvelope = copy(requiresAcknowledge = b)

  // For the default we simply do nothing when a downstream consumer calls acknowledge
  def acknowledge(): Unit = ackInfo.foreach { i => i.session.ack(i.jmsMessage) }
}
