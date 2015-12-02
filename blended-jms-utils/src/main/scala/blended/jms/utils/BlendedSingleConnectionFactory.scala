package blended.jms.utils

import javax.jms.{Connection, ConnectionFactory, JMSException}

import akka.pattern.ask
import akka.actor.Props
import akka.util.Timeout
import blended.akka.OSGIActorConfig
import blended.jms.utils.internal.{ConnectionControlActor, GetConnection}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

class BlendedSingleConnectionFactory(
  cfg : OSGIActorConfig,
  cf: ConnectionFactory,
  provider : String,
  pingInterval : Int
) extends ConnectionFactory {

  private[this] implicit val eCtxt = cfg.system.dispatcher
  private[this] implicit val timeout = Timeout(100.millis)
  private[this] val log : Logger = LoggerFactory.getLogger(classOf[BlendedSingleConnectionFactory])

  private[this] val con = s"JMS-$provider"
  private[this] val actor = cfg.system.actorOf(Props(ConnectionControlActor(provider, cf, pingInterval)), con)

  log.debug(s"Created Connection control actor [$actor]")

  @throws[JMSException]
  override def createConnection(): Connection = {

    log.debug(s"creating connection for provider [$provider]")

    try {

      val futConn = for {
        controller <- cfg.system.actorSelection(s"/user/$con").resolveOne()
        conn <- (controller ? GetConnection).mapTo[Connection]
      } yield conn

      Await.result(futConn, timeout.duration)
    } catch {
      case e: Exception => {
        val jmsEx = new JMSException("Error getting Connection Factory")
        jmsEx.setLinkedException(e)
        throw jmsEx
      }
    }
  }

  override def createConnection(user: String, password: String): Connection = {
    log.warn("BlendedSingleConnectionFactory.createConnection() called with username and password, which is not supported.\nFalling back to default username and password.")
    createConnection()
  }
}
