package blended.file

import java.io.{File, FileOutputStream}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import blended.akka.SemaphoreActor
import blended.testsupport.TestActorSys
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

import scala.concurrent.duration._

class FilePollSpec extends LoggingFreeSpec with Matchers {

  def genFile(f: File) : Unit = {
    val os = new FileOutputStream(f)
    os.write("Hallo Andreas".getBytes())
    os.flush()
    os.close()
  }

  private[this] def withBlocking(lockfile : String, testkit: TestKit) : Unit = {

    implicit val system = testkit.system
    val sem : ActorRef = system.actorOf(Props[SemaphoreActor])

    val srcDir = new File(System.getProperty("projectTestOutput") + "/blocking")
    srcDir.mkdirs()

    val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
      sourceDir = srcDir.getAbsolutePath(),
      lock = Some(lockfile)
    )

    val lockFile = if (lockfile.startsWith("./")) {
      new File(cfg.sourceDir, lockfile.substring(2))
    } else {
      new File(lockfile)
    }

    genFile(lockFile)

    val srcFile = new File(srcDir, "test.txt")
    genFile(srcFile)

    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[FileProcessed])

    val handler = new SucceedingFileHandler()
    system.actorOf(FilePollActor.props(cfg, handler, Some(sem)))

    probe.expectNoMessage(3.seconds)
    srcFile.exists() should be (true)

    lockFile.delete()

    probe.expectMsgType[FileProcessed]
    srcFile.exists() should be (false)
    handler.handled should have size(1)

    handler.handled.head.f.getName() should be ("test.txt")
  }

  "The File Poller should" - {

    "do perform a regular poll and process files" in TestActorSys { testkit =>

      implicit val system : ActorSystem = testkit.system

      val sem : ActorRef = system.actorOf(Props[SemaphoreActor])

      val srcDir = new File(System.getProperty("projectTestOutput") + "/pollspec")
      srcDir.mkdirs()

      val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
        sourceDir = srcDir.getAbsolutePath()
      )

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[FileProcessed])
      val handler = new SucceedingFileHandler()
      val actor = system.actorOf(FilePollActor.props(cfg, handler, Some(sem)))
      probe.expectNoMessage(3.seconds)

      val f = new File(srcDir, "test1.txt")
      genFile(f)

      probe.expectMsgType[FileProcessed]
      f.exists() should be (false)

      val files = List(
        new File(srcDir, "test2.txt"),
        new File(srcDir, "test3.xml"),
        new File(srcDir, "test4.txt")
      )

      files.foreach(genFile)

      probe.expectMsgType[FileProcessed]
      probe.expectMsgType[FileProcessed]

      files.forall{ f => (f.getName().endsWith("txt") && !f.exists()) || (!f.getName().endsWith("txt") && f.exists()) } should be (true)

      handler.handled should have size(3)
      handler.handled.map(_.f.getName()).sorted should be (List("test4.txt", "test2.txt", "test1.txt").sorted)
    }

    "block the message processing if specified lock file exists (relative)" in TestActorSys { testkit =>
      withBlocking("./lock", testkit)
    }

    "block the message processing if specified lock file exists (absolute)" in TestActorSys { testkit =>
      withBlocking("/tmp/lock", testkit)
    }
  }
}
