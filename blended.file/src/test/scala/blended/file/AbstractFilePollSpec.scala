package blended.file

import java.io.{File, FileOutputStream}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import blended.akka.SemaphoreActor
import blended.util.logging.Logger
import org.apache.commons.io.FileUtils
import org.scalatest.Matchers

import scala.concurrent.duration._

trait AbstractFilePollSpec { this : Matchers =>

  private val log : Logger = Logger[AbstractFilePollSpec]

  protected def handler()(implicit system : ActorSystem) : FilePollHandler

  protected def genFile(f: File) : Unit = {
    log.info(s"Creating file [${f.getAbsolutePath()}]")
    val os = new FileOutputStream(f)
    os.write("Hallo Andreas".getBytes())
    os.flush()
    os.close()
  }

  protected def semaphore()(implicit system : ActorSystem) : Option[ActorRef] =
    Some(system.actorOf(Props[SemaphoreActor]))

  protected def filePoller(cfg : FilePollConfig)(implicit system: ActorSystem) : ActorRef =
    system.actorOf(FilePollActor.props(cfg, handler(), semaphore()))

  protected val defaultTest : List[File] => TestProbe => Unit = files => probe => {
    val result : List[File] = files.filter(_.getName.endsWith("txt"))
    val processCount : Int = result.size

    val processed : List[FileProcessResult] = probe.receiveWhile[FileProcessResult](max = 10.seconds, messages = files.size) {
      case fp : FileProcessResult => fp
    }.toList

    files.forall{ f =>
      (f.getName().endsWith("txt") && !f.exists()) || (!f.getName().endsWith("txt") && f.exists())
    } should be (true)

    val names : List[String] = files.map(_.getName())

    processed should have size processCount
    assert(
      processed.forall(p => names.contains(p.cmd.originalFile.getName()))
    )
  }

  protected def withMessages(
    dir : String,
    msgCount : Int
  )(f : List[File] => TestProbe => Unit)(implicit system : ActorSystem) : List[File] = {

    val srcDir = new File(System.getProperty("projectTestOutput") + "/" + dir)
    FileUtils.deleteDirectory(srcDir)
    srcDir.mkdirs()

    val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
      sourceDir = srcDir.getAbsolutePath()
    )

    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[FileProcessResult])

    val actor : ActorRef = filePoller(cfg)

    val files : List[File] = 1.to(msgCount).map { i =>
      val f = new File(srcDir, s"test$i." + (if (i % 2 == 0) "txt" else "xml"))
      genFile(f)
      f
    }.toList

    f(files)(probe)

    system.stop(actor)

    files.filter(_.getName().matches(cfg.pattern.getOrElse(".*")))
  }
}
