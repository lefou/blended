package blended.streams.jms

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic}
import blended.jms.utils.{JmsAckSession, JmsDestination}
import blended.streams
import blended.streams.message.{AckInfo, FlowEnvelope}
import blended.util.logging.Logger
import javax.jms._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

final class JmsAckSourceStage(settings: JMSConsumerSettings, actorSystem : ActorSystem)
  extends GraphStage[SourceShape[FlowEnvelope]] {

  sealed trait TimerEvent
  private case object Ack extends TimerEvent
  private case class Poll(s : String) extends TimerEvent

  private val out = Outlet[FlowEnvelope]("JmsSource.out")

  override def shape: SourceShape[FlowEnvelope] = SourceShape[FlowEnvelope](out)

  override protected def initialAttributes: Attributes =
    ActorAttributes.dispatcher("FixedPool")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {

    val logic = new SourceStageLogic[JmsAckSession](shape, out, settings, inheritedAttributes) {

      private[this] val log = Logger(classOf[JmsAckSourceStage].getName())
      private[this] val inflight : mutable.Map[String, FlowEnvelope] = mutable.Map.empty
      private[this] val consumer : mutable.Map[String, MessageConsumer] = mutable.Map.empty

      override private[jms] val handleError = getAsyncCallback[Throwable]{ ex =>
        fail(out, ex)
      }

      private val acknowledge : FlowEnvelope => Unit = { env =>
        env.ackInfo.foreach { info =>
          log.debug(s"Acknowledging message for session id [${info.session.sessionId}] : ${env.flowMessage}")
          try {
            // the acknowlede might not work a message might be in inflight while the previous message has not been
            // acknowledged. In this case we will have closed the session and created a new one.
            info.jmsMessage.acknowledge()
            scheduleOnce(Poll(info.session.sessionId), 10.millis)
          } catch {
            case e: JMSException =>
              log.error(e)(s"Error acknowledging message for session [${info.session.sessionId}] : [${env.flowMessage}]")
              closeSession(info.session)
          } finally {
            inflight -= info.session.sessionId
          }
        }
      }

      private[this] def ackQueued(): Unit = {

        inflight.foreach { case (sessionId, envelope) =>
          envelope.ackInfo.foreach { info =>
            Option(info.session.ackQueue.poll()) match {
              case Some(action) =>
                action match {
                  case Right(sessionId) =>
                    inflight.get(sessionId).foreach(acknowledge)
                    ackQueued()

                  case Left(t) =>
                    failStage(t)
                }
              case None =>
                if (System.currentTimeMillis() - info.created > jmsSettings.ackTimeout.toMillis) {
                  log.debug(s"Acknowledge timed out for [$sessionId] with message ${envelope.flowMessage}")
                  inflight -= sessionId
                  // Recovering the session, so that unacknowledged messages may be redelivered
                  closeSession(info.session)
                }
            }
          }
        }
      }

      private[this] def poll(sid : String) : Future[Unit] = Future {

        (jmsSessions.get(sid), consumer.get(sid)) match {
          case (Some(session), Some(c)) =>

            // TODO: Make the receive timeout configurable
            Option(c.receive(100)) match {
              case Some(message) =>
                val flowMessage = JmsFlowSupport.jms2flowMessage(jmsSettings, message).get
                log.debug(s"Message received for [${session.sessionId}] : $flowMessage")
                try {

                  val info = AckInfo(
                    jmsMessage = message,
                    session = session
                  )

                  val envelope = FlowEnvelope(flowMessage)
                    .withRequiresAcknowledge(true)
                    .withAckInfo(Some(info)
                  )
                  inflight += (session.sessionId -> envelope)
                  handleMessage.invoke(envelope)
                } catch {
                  case e: JMSException =>
                    handleError.invoke(e)
                }

              case None => scheduleOnce(Poll(sid), 10.millis)
            }
          case (_, _) => scheduleOnce(Poll(sid), 100.millis)
        }
      }(ec)

      override def preStart(): Unit = {
        super.preStart()
        schedulePeriodically(Ack, 10.millis)
      }

      override def postStop(): Unit = {
        super.postStop()
        cancelTimer(Poll)
        cancelTimer(Ack)
      }

      override protected def createSession(connection: Connection): JmsAckSession = {

        jmsSettings.jmsDestination match {
          case Some(d) =>
            val session = connection.createSession(false, AcknowledgeMode.ClientAcknowledge.mode)
            new JmsAckSession(
              connection = connection,
              session = session,
              sessionId = nextSessionId,
              jmsDestination = d
            )
          case None =>
            val msg = s"Destination must be set for consumer in [$id]"
            log.error(msg)
            throw new IllegalArgumentException(msg)
        }
      }

      private[this] def closeSession(session: JmsAckSession) : Unit = {

        try {
          log.debug(s"Closing session [${session.sessionId}]")
          session.closeSessionAsync().onComplete { _ =>
            consumer -= session.sessionId
            jmsSessions -= session.sessionId
            onSessionClosed()
          }
        } catch {
          case _ : Throwable =>
            log.error(s"Error closing session with id [${session.sessionId}]")
        }
      }

      private[this] def onSessionClosed() : Unit = initSessionAsync()

      override protected def onTimer(timerKey: Any): Unit = {

        timerKey match {
          case Ack => ackQueued()
          case p : Poll =>
            poll(p.s)
          case _ =>
        }
      }

      private val dest : JmsDestination = jmsSettings.jmsDestination match {
        case Some(d) => d
        case None => throw new Exception("Destination must be set for Consumer")
      }

      protected def pushMessage(msg: FlowEnvelope): Unit = {
        push(out, msg)
      }

      private[this] def createConsumer(session: JmsAckSession) : Unit = {
        log.debug(s"Creating message consumer for session [${session.sessionId}]")
        session.createConsumer(settings.selector).onComplete {

          case Success(c) =>
            consumer += (session.sessionId -> c)
            scheduleOnce(Poll(session.sessionId), 100.millis)

          case Failure(e) =>
            fail.invoke(e)
        }
      }

      override protected def onSessionOpened(jmsSession: JmsAckSession): Unit =

        jmsSession match {
          case session: JmsAckSession =>
            createConsumer(session)
          case _ =>
            throw new IllegalArgumentException(
              "Session must be of type JMSAckSession, it is a " + jmsSession.getClass.getName
            )
        }
    }

    logic
  }
}
