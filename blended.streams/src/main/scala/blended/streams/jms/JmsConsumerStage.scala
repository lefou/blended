package blended.streams.jms

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage.{AsyncCallback, GraphStage, GraphStageLogic}
import blended.jms.utils.{JmsDestination, JmsSession}
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope, FlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import blended.streams.{AckSourceLogic, DefaultAcknowledgeContext}
import blended.util.RichTry._
import blended.util.logging.Logger
import javax.jms.{Message, MessageConsumer}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * The JmsSource realizes an inbound Stream of FlowMessages consumed from
 * a given JMS destination. The messages can be consumed using an arbitrary sink
 * of FlowMessages. The sink must acknowledge or deny the message eventually. If
 * the message is not acknowledged with a given time frame, the message will be
 * denied and eventually redelivered.
 *
 * A JMS session will only consume one message at a time, the next message will
 * be consumed only after the previous message of that session has been denied
 * or acknowledged. The messages that are waiting for acknowledgement are maintained
 * within a map of (session-id / inflight messages).
 *
 * Any exception while consuming a message within a session will cause that session
 * to close, so that any inflight messages for that session will be redelivered. A
 * new session will be created automatically for any sessions that have been closed
 * with such an exception.
 *
 * One of the use cases is a retry pattern. In that pattern the message must remain
 * in the underlying destination for a minimum amount of time (i.e. 5 seconds).
 * The message receive loop will check the JMSTimestamp against the System.currentTimeMillis
 * to check whether a message can be passed downstream already.
 */
final class JmsConsumerStage(
  name : String,
  consumerSettings : JMSConsumerSettings,
  minMessageDelay : Option[FiniteDuration] = None
)(implicit actorSystem : ActorSystem)
  extends GraphStage[SourceShape[FlowEnvelope]] {

  consumerSettings.log.debug(s"Starting consumer [$name]")

  private[this] val consumer : mutable.Map[String, MessageConsumer] = mutable.Map.empty

  private[this] def addConsumer(s : String, c : MessageConsumer) : Unit = {
    consumer.put(s, c)
    consumerSettings.log.debug(s"Jms Consumer count of [$name] is [${consumer.size}]")
  }

  private[this] def removeConsumer(s : String) : Unit = {
    consumer.remove(s)
    consumerSettings.log.debug(s"Consumer count of [$name] is [${consumer.size}]")
  }

  private val headerConfig : FlowHeaderConfig = consumerSettings.headerCfg
  private val out : Outlet[FlowEnvelope] = Outlet[FlowEnvelope](s"JmsAckSource($name.out)")
  override val shape : SourceShape[FlowEnvelope] = SourceShape[FlowEnvelope](out)

  private class JmsAckContext(
    inflightId : String,
    env : FlowEnvelope,
    val jmsMessage : Message,
    val jmsMessageAck : Message => Unit,
    val session : JmsSession,
    val sessionClose : JmsSession => Unit
  ) extends DefaultAcknowledgeContext(inflightId, env, System.currentTimeMillis()) {

    override def deny(): Unit = {
      sessionClose(session)
      consumerSettings.log.info(s"Message [${envelope.id}] has been denied. Closing receiving session.")
    }

    override def acknowledge(): Unit = {
      jmsMessageAck(jmsMessage)
      consumerSettings.log.info(s"Acknowledged envelope [${envelope.id}] for session [${session.sessionId}]")
    }
  }

  private class JmsSourceLogic() extends AckSourceLogic[JmsAckContext](shape, out) {

    /** The id to identify the instance in the log files */
    override protected val id: String = name

    override val log: Logger = consumerSettings.log
    override val autoAcknowledge: Boolean = consumerSettings.acknowledgeMode == AcknowledgeMode.AutoAcknowledge

    private val handleError : AsyncCallback[Throwable] = getAsyncCallback[Throwable](t => failStage(t))

    private val srcDest : JmsDestination = consumerSettings.jmsDestination match {
      case Some(d) => d
      case None => throw new IllegalArgumentException(s"Destination must be set for consumer in [$id]")
    }

    private val closeSession : AsyncCallback[JmsSession] = getAsyncCallback(s => connector.closeSession(s.sessionId))
    private val ackMessage : AsyncCallback[Message] = getAsyncCallback[Message](m => m.acknowledge())

    private lazy val connector : JmsConnector = new JmsConnector(id, consumerSettings)(session => Try {
      consumerSettings.log.debug(
        s"Creating message consumer for session [${session.sessionId}], " +
          s"destination [$srcDest] and selector [${consumerSettings.selector}]"
      )
      session.createConsumer(srcDest, consumerSettings.selector) match {

        case Success(c) =>
          addConsumer(session.sessionId, c)

        case Failure(e) =>
          consumerSettings.log.debug(s"Failed to create consumer for session [${session.sessionId}] : [${e.getMessage()}]")
          closeSession.invoke(session)
      }
    })( s => Try {
      removeConsumer(s.sessionId)
    })(handleError.invoke)


    override protected def freeInflightSlot(): Option[String] =
      determineNextSlot(inflightSlots.filter(id => connector.isOpen(id))) match {
        case Some(s) => Some(s)
        case None => determineNextSlot(inflightSlots)
      }

    /** The id's of the available inflight slots */
    override protected val inflightSlots : List[String] =
      1.to(consumerSettings.sessionCount).map(i => s"$id-$i").toList

    private var nextPollRelative : Option[FiniteDuration] = None
    override protected def nextPoll(): Option[FiniteDuration] =
      Some(nextPollRelative.getOrElse(consumerSettings.pollInterval))

    private def receive(session : JmsSession) : Try[Option[Message]] = Try {

      val msg : Option[Message] = consumer.get(session.sessionId).flatMap { c =>
        if (consumerSettings.receiveTimeout.toMillis <= 0) {
          Option(c.receiveNoWait())
        } else {
          Option(c.receive(consumerSettings.receiveTimeout.toMillis))
        }
      }

      val result : Option[Message] = msg match {
        case None => None

        case Some(m) =>
          minMessageDelay match {
            case Some(d) =>
              val age : Long = System.currentTimeMillis() - m.getJMSTimestamp()
              if (age <= d.toMillis) {
                closeSession.invoke(session)
                nextPollRelative = Some( (d.toMillis - age).millis )
                consumerSettings.log.debug(s"Message has not reached the minimum message delay yet ...rescheduling in [$nextPollRelative]")
                None
              } else {
                nextPollRelative = None
                Some(m)
              }
            case None =>
              msg
          }
      }

      result
    }

    private def createEnvelope(message : Message, ackHandler : AcknowledgeHandler) : Try[FlowEnvelope] = Try {

      val flowMessage : FlowMessage = JmsFlowSupport.jms2flowMessage(headerConfig)(consumerSettings)(message).unwrap

      val envelopeId : String = flowMessage.header[String](headerConfig.headerTransId) match {
        case None =>
          val newId = UUID.randomUUID().toString()
          consumerSettings.log.trace(s"Created new envelope id [$newId]")
          newId
        case Some(s) =>
          consumerSettings.log.trace(s"Reusing transaction id [$s] as envelope id")
          s
      }

      FlowEnvelope(flowMessage, envelopeId)
        .withHeader(headerConfig.headerTransId, envelopeId).unwrap
        .withRequiresAcknowledge(true)
        .withAckHandler(Some(ackHandler))
    }

    override protected def doPerformPoll(id: String, ackHandler: AcknowledgeHandler): Try[Option[JmsAckContext]] = Try {
      connector.getSession(id) match {
        case Some(s) =>

          receive(s).unwrap
            .map{ m =>
              val e : FlowEnvelope = createEnvelope(m, ackHandler).unwrap

              new JmsAckContext(
                inflightId = id,
                env = e,
                jmsMessage = m,
                jmsMessageAck = ackMessage.invoke,
                session = s,
                sessionClose = closeSession.invoke
              )
            }

        case None =>
          None
      }
    }

    override def postStop(): Unit = {
      log.debug(s"Stopping JmsConsumerStage [$id].")
      connector.closeAll()
    }

  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new JmsSourceLogic()
}
