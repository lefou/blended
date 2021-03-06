package blended.mgmt.agent.internal

import scala.collection.immutable.Queue
import scala.concurrent.duration.DurationLong
import scala.util.Try

import akka.actor.Actor
import akka.actor.Cancellable
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import blended.prickle.akka.http.PrickleSupport
import blended.updater.config._
import com.typesafe.config.Config
import blended.util.logging.Logger

/**
 * Actor, that collects various container information and send's it to a remote management container.
 *
 * Sources of information:
 *
 * * [[ServiceInfo]] from the Akka event stream
 * * `([[Long]], List[[[Profile]]])` from the Akka event stream
 *
 * Send to remote container:
 *
 * * [[ContainerInfo]] send via HTTP POST request
 *
 * Configuration:
 *
 * This actor reads a configuration class [[MgmtReporterConfig]] from the [[OSGIActorConfig]].
 * Only if all necessary configuration are set (currently `initialUpdateDelayMsec` and `updateIntervalMsec`), the reporter sends information to the management container.
 * The target URL of the management container is configured with the `registryUrl` config entry.
 *
 */
trait MgmtReporter extends Actor with PrickleSupport {

  import MgmtReporter._
  import blended.updater.config.json.PrickleProtocol._

  ////////////////////
  // ABSTRACT
  protected val config: Try[MgmtReporterConfig]
  //
  protected def createContainerInfo: ContainerInfo
  ////////////////////

  ////////////////////
  // MUTABLE
  private[this] var _ticker: Option[Cancellable] = None
  private[this] var _serviceInfos: Map[String, ServiceInfo] = Map()
  private[this] var _lastProfileInfo: ProfileInfo = ProfileInfo(0L, Nil)
  private[this] var _appliedUpdateActionIds: List[String] = List()
  ////////////////////

  private[this] lazy val log = Logger[MgmtReporter]

  implicit private[this] lazy val eCtxt = context.system.dispatcher
  implicit private[this] lazy val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  protected def serviceInfos: Map[String, ServiceInfo] = _serviceInfos

  protected def profileInfo: ProfileInfo = _lastProfileInfo

  //  protected def appliedUpdateActionIds = _appliedUpdateActionIds
  //  protected def clearAppliedUpdateActions(ids: List[String]) = _appliedUpdateActionIds = _appliedUpdateActionIds.filter(ids.contains)

  override def preStart(): Unit = {
    super.preStart()

    config foreach { config =>
      if (config.initialUpdateDelayMsec < 0 || config.updateIntervalMsec <= 0) {
        log.warn("Inapropriate timing configuration detected. Disabling automatic container status reporting")
      } else {
        log.info(s"Activating automatic container status reporting with update interval [${config.updateIntervalMsec}]")
        _ticker = Some(context.system.scheduler.schedule(config.initialUpdateDelayMsec.milliseconds, config.updateIntervalMsec.milliseconds, self, Tick))
      }
    }

    context.system.eventStream.subscribe(context.self, classOf[ServiceInfo])
    context.system.eventStream.subscribe(context.self, classOf[ProfileInfo])
    context.system.eventStream.subscribe(context.self, classOf[UpdateActionApplied])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(context.self)

    _ticker.foreach(_.cancel())
    _ticker = None

    super.postStop()
  }

  def receive: Receive = LoggingReceive {

    case Tick =>
      config.foreach { config =>

        // we submit the applied update actions to the mgmt server
        val appliedUpdateActions = _appliedUpdateActionIds

        val info = createContainerInfo.copy(appliedUpdateActionIds = appliedUpdateActions)
        log.debug(s"Performing report [${info}].")

        val entity = Marshal(info).to[MessageEntity]

        val request = entity.map { entity =>
          HttpRequest(
            uri = config.registryUrl,
            method = HttpMethods.POST,
            entity = entity
          )
        }

        // TODO think about ssl
        val responseFuture = request.flatMap { request =>
          Http(context.system).singleRequest(request)
        }.map(r => r -> appliedUpdateActions)

        import akka.pattern.pipe
        responseFuture.pipeTo(self)
      }

    case (response @ HttpResponse(status, headers, entity, protocol), appliedUpdateActionIds: List[String]) =>
      status match {
        case StatusCodes.OK =>
          // As the server accepted also the list of applied update action IDs
          // we remove those from the list
          _appliedUpdateActionIds = _appliedUpdateActionIds.filterNot(appliedUpdateActionIds.contains)

          import akka.pattern.pipe

          // OK; unmarshal and process
          Unmarshal(entity).to[ContainerRegistryResponseOK].pipeTo(self)

        case _ =>
          log.warn(s"Non-OK response ${config.map(c => c.registryUrl).getOrElse("")} from node: ${response}")
          response.discardEntityBytes()
      }

    case ContainerRegistryResponseOK(id, actions) =>
      log.debug(s"Reported [${id}] to management node")
      if (!actions.isEmpty) {
        log.info(s"Received ${actions.size} update actions from management node: ${actions}")
        actions.foreach { action: UpdateAction =>
          log.debug(s"Publishing event to event stream: ${action}")
          context.system.eventStream.publish(action)
        }
      }

    // from event stream
    case serviceInfo @ ServiceInfo(name, svcType, ts, lifetime, props) =>
      log.debug(s"Update service info for: ${name}")
      _serviceInfos += name -> serviceInfo

    // from event stream
    case pi @ ProfileInfo(timestamp, _) =>
      if (timestamp > _lastProfileInfo.timeStamp) {
        log.debug("Update profile info to: " + pi)
        _lastProfileInfo = pi
      } else {
        log.debug(s"Ingnoring profile info with timestamp [${timestamp.underlying()}] which is older than [${_lastProfileInfo.timeStamp.underlying()}]: ${pi}")
      }

    case UpdateActionApplied(id, _) =>
      _appliedUpdateActionIds ::= id

  }
}

object MgmtReporter {

  object MgmtReporterConfig {
    def fromConfig(config: Config): Try[MgmtReporterConfig] = Try {
      MgmtReporterConfig(
        registryUrl = config.getString("registryUrl"),
        updateIntervalMsec = if (config.hasPath("updateIntervalMsec")) config.getLong("updateIntervalMsec") else 0,
        initialUpdateDelayMsec = if (config.hasPath("initialUpdateDelayMsec")) config.getLong("initialUpdateDelayMsec") else 0
      )
    }
  }

  case class MgmtReporterConfig(
    registryUrl: String,
    updateIntervalMsec: Long,
    initialUpdateDelayMsec: Long
  ) {

    override def toString(): String = s"${getClass().getSimpleName()}(registryUrl=${registryUrl},updateInetervalMsec=${updateIntervalMsec},initialUpdateDelayMsec=${initialUpdateDelayMsec})"
  }

  case object Tick

}
