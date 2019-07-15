package blended.streams

import akka.Done
import akka.actor.Actor
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches, Materializer}
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

trait StreamControllerSupport[T, Mat] { this : Actor =>

  private[this] val log : Logger = Logger(getClass().getName())
  private[this] val rnd = new Random()
  private[this] implicit val materializer : Materializer = ActorMaterializer()
  private[this] implicit val eCtxt : ExecutionContext = context.dispatcher

  val nextInterval : FiniteDuration => StreamControllerConfig => FiniteDuration = { interval => streamCfg =>

    val noise = {
      val d = rnd.nextDouble().abs
      (d - d.floor) / (1 / (streamCfg.random * 2)) - streamCfg.random
    }

    var newIntervalMillis : Double =
      if (streamCfg.exponential) {
        interval.toMillis * 2
      } else {
        interval.toMillis + streamCfg.minDelay.toMillis
      }

    newIntervalMillis = scala.math.min(
      streamCfg.maxDelay.toMillis,
      newIntervalMillis + newIntervalMillis * noise
    )

    newIntervalMillis.toLong.millis
  }

  private def restart(streamCfg : StreamControllerConfig, interval : FiniteDuration, startedAt : Option[Long]) : Unit = {
    val nextStart : FiniteDuration = startedAt match {
      case None => nextInterval(interval)(streamCfg)
      case Some(s) =>
        if (System.currentTimeMillis() - s < streamCfg.maxDelay.toMillis) {
          nextInterval(interval)(streamCfg)
        } else {
          streamCfg.minDelay
        }
    }

    log.info(s"Scheduling restart of Stream [${streamCfg.name}] in [$nextStart]")

    context.system.scheduler.scheduleOnce(nextStart, self, StreamController.Start)

    context.become(starting(streamCfg, nextStart))
  }

  def starting(streamCfg : StreamControllerConfig, interval : FiniteDuration) : Receive = {
    case StreamController.Stop =>
      context.stop(self)

    case StreamController.Start =>
      log.debug(s"Initializing StreamController [${streamCfg.name}]")

      startStream() match {
        case Success( (mat, killswitch, done) ) =>
          done.onComplete {
            case Success(_) =>
              self ! StreamController.StreamTerminated(None)
            case Failure(t) =>
              self ! StreamController.StreamTerminated(Some(t))
          }

          context.become(running(streamCfg, killswitch, interval, System.currentTimeMillis()))

        case Failure(t) =>
          restart(streamCfg, interval, None)
      }

  }

  def running(
    streamCfg : StreamControllerConfig, killSwitch : KillSwitch, interval : FiniteDuration, startedAt : Long
  ) : Receive = {
    case StreamController.Stop =>
      killSwitch.shutdown()
      context.become(stopping)

    case StreamController.Abort(t) =>
      killSwitch.abort(t)
      context.become(stopping)

    case StreamController.StreamTerminated(t) =>
      log.info(s"Stream [${streamCfg.name}] terminated ...")
      if (t.isDefined || (!streamCfg.onFailureOnly)) {
        t.foreach { e =>
          log.error(e)(e.getMessage)
        }

        restart(streamCfg, interval, Some(startedAt))
      } else {
        context.stop(self)
      }
  }

  def stopping : Receive = {
    case StreamController.StreamTerminated(_) => context.stop(self)
  }

  private def startStream() : Try[(Mat, KillSwitch, Future[Done])] = Try {
    val ((m, s), d) = source()
      .viaMat(KillSwitches.single)(Keep.both)
      .watchTermination()(Keep.both)
      .toMat(Sink.ignore)(Keep.left)
      .run()

    (m, s, d)
  }

  def source() : Source[T, Mat]
}
