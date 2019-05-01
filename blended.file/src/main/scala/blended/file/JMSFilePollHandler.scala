package blended.file
import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.{ActorMaterializer, FlowShape, Graph, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Source, Zip}
import akka.util.ByteString
import blended.akka.MemoryStash
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

import scala.concurrent.{Future, Promise}
import scala.io.BufferedSource
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object AsyncSendActor {

  def props(
    settings : JmsProducerSettings,
    header : FlowMessage.FlowMessageProps
  ) : Props = Props(new AsyncSendActor(settings, header))
}

class AsyncSendActor(
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps
) extends Actor with MemoryStash with JmsStreamSupport {

  private val log : Logger = Logger[AsyncSendActor]
  private implicit val system : ActorSystem = context.system
  private implicit val materializer : Materializer = ActorMaterializer()

  case object Start

  override def preStart(): Unit = self ! Start

  case class FileSendInfo(
    actor : ActorRef,
    cmd : FileProcessCmd,
    env : FlowEnvelope,
    p : Promise[FileProcessResult]
  )

  private def createEnvelope(cmd : FileProcessCmd) : Try[FlowEnvelope] = {

    val src : BufferedSource = scala.io.Source.fromFile(cmd.fileToProcess)

    try {
      val body : ByteString = ByteString(src.mkString)
      src.close()

      val msg : FlowMessage = FlowMessage(body)(header)
        .withHeader("BlendedFileName", cmd.originalFile.getName()).get
        .withHeader("BlendedFilePath", cmd.originalFile.getAbsolutePath()).get

      Success(FlowEnvelope(msg, cmd.id))
    } catch {
      case NonFatal(t) => Failure(t)
    } finally {
      src.close()
    }
  }

  override def receive: Receive = starting.orElse(stashing)

  private def sendStream() : ActorRef = {

    // We create an outbound JMS Stream with an actor serving as the entryPoint
    val src : Source[FileSendInfo, ActorRef] =
      Source.actorRef[FileSendInfo](FilePollActor.batchSize * 2, overflowStrategy = OverflowStrategy.fail)

    val performSend : Flow[FileSendInfo, FileSendInfo, NotUsed] = {

      val g : Graph[FlowShape[FileSendInfo, FileSendInfo], NotUsed] = GraphDSL.create() { implicit b=>

        import GraphDSL.Implicits._

        // First we spilt the flow, so that we can keep the file info in one part
        // and perform the send in the other part, collecting any exceptions that
        // may occurr
        val split = b.add(Broadcast[FileSendInfo](2))

        // to send the jms message we need to select the envelope
        val select = b.add(Flow.fromFunction[FileSendInfo, FlowEnvelope](_.env))

        // then we perform the jms send
        val jmsSend = b.add(jmsProducer(
          name = "filesend",
          settings = settings
        ))

        split.out(0) ~> select ~> jmsSend.in

        // Finally we zip the branches
        val zip = b.add(Zip[FlowEnvelope, FileSendInfo])

        jmsSend.out ~> zip.in0
        split.out(1) ~> zip.in1

        val merge = b.add(Flow.fromFunction[(FlowEnvelope, FileSendInfo), FileSendInfo]{ p => p._2.copy(env = p._1)})
        zip.out ~> merge.in

        FlowShape(split.in, merge.out)
      }

      Flow.fromGraph(g)
    }

    // This is to send the completed info object back to the controlling actor after sending the message
    // The flow envelope will have an exception set if the send has failed
    val respond : Flow[FileSendInfo, FileSendInfo, NotUsed] = Flow.fromFunction[FileSendInfo, FileSendInfo]{ info =>
      info.actor ! info
      info
    }

    self
  }

  private def starting : Receive = {
    case Start =>
      log.info(s"Starting Async Send Actor...")
      context.become(started(sendStream()))
  }

  private def started(sendStream : ActorRef) : Receive = {
    case (cmd : FileProcessCmd, p : Promise[FileProcessResult]) =>

      createEnvelope(cmd) match {
        case Success(env) =>
          sendStream ! FileSendInfo(self, cmd, env, p)
        case Failure(t) =>
          p.failure(t)
      }

    case info : FileSendInfo =>
      info.env.exception match {
        case None =>
          log.info(s"Successfully processed file [${info.cmd.id}] : [${info.cmd}]")
          info.p.success(FileProcessResult(info.cmd, None))

        case Some(t) => info.p.failure(t)
      }
  }
}

class JMSFilePollHandler(
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps,
  bufferSize : Int
)(implicit system: ActorSystem) extends FilePollHandler with JmsStreamSupport {

  private val log : Logger = Logger[JMSFilePollHandler]
  private var processActor : Option[ActorRef] = None

  def start() : Unit = { processActor.synchronized {
    if (processActor.isEmpty) {
      processActor = Some(system.actorOf(AsyncSendActor.props(settings, header)))
    }
  }}

  def stop() : Unit = {
    processActor.synchronized{
      processActor.foreach(system.stop)
      processActor = None
    }
  }

  override def processFile(cmd: FileProcessCmd) : Future[FileProcessResult] = {

    val p : Promise[FileProcessResult] = Promise[FileProcessResult]()

    processActor match {
      case None =>
        val msg = s"Actor to process file [${cmd.fileToProcess}] for [${cmd.id}] is not available"
        log.warn(msg)
        p.success(FileProcessResult(cmd, Some(new Exception(msg))))
      case Some(a) =>
        a ! (cmd, p)
    }

    p.future
  }
}
