package blended.itestsupport.jms

import javax.jms.DeliveryMode

import akka.actor.ActorRef

package protocol {

  import blended.jms.utils.JMSMessageFactory

  case class Connect(clientId: String, user: Option[String] = None, password: Option[String] = None)
  case class Connected(clientId: String)

  case object Disconnect
  case object Disconnected

  case class CreateProducer(destName: String, msgCounter: Option[ActorRef] = None)
  case class CreateConsumer(destName: String, msgCounter: Option[ActorRef] = None)
  case class CreateDurableSubscriber(topic: String, subScriberName: String, msgCounter: Option[ActorRef] = None)

  case object StopConsumer

  case class ProduceMessage(
    msgFactory: JMSMessageFactory,
    content: Option[Any] = None,
    count : Int = 1,
    deliveryMode : Int = DeliveryMode.NON_PERSISTENT,
    priority : Int = 4,
    ttl : Long = 0
  )
  case object MessageProduced

  case class ProducerActor(producer: ActorRef)
  case class ConsumerActor(consumer: ActorRef)

  case object Unsubscribe
  case class ConsumerStopped(destName: String)

  case class JMSCaughtException(inner: Throwable)
}
