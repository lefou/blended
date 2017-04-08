package blended.jms.utils

import javax.jms.ConnectionFactory

import blended.akka.{ActorSystemWatching, OSGIActorConfig}
import blended.util.ReflectionHelper
import domino.DominoActivator
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

abstract class ConnectionFactoryFactory extends DominoActivator with ActorSystemWatching {

  val providerName : String
  def createConnectionFactory(cfg: OSGIActorConfig) : ConnectionFactory

  private[this] val log : Logger = LoggerFactory.getLogger(classOf[ConnectionFactoryFactory])

  protected def configureConnectionFactory(cf: ConnectionFactory, cfg: OSGIActorConfig) : Unit = {

    log.info(s"Configuring connection factory of type [${cf.getClass().getName()}].")
    val symbolicName = bundleContext.getBundle().getSymbolicName

    if (cfg.config.hasPath("properties")) {
      val propCfg = cfg.config.getObject("properties")

      propCfg.entrySet().asScala.foreach { entry =>

        val key = entry.getKey
        val value = entry.getValue.unwrapped()

        log.info(s"Setting property [$key] for connection factory [$symbolicName] to [$value].")
        ReflectionHelper.setProperty(cf, value, key)
      }
    }
  }

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      val cf = createConnectionFactory(cfg)
      configureConnectionFactory(cf, cfg)

      val singleConnectionFactory = new BlendedSingleConnectionFactory(cfg, cf, providerName)
      singleConnectionFactory.providesService[ConnectionFactory]("provider" -> providerName)
    }
  }

}
