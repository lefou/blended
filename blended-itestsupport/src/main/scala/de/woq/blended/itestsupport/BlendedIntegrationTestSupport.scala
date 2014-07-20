package de.woq.blended.itestsupport

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import de.woq.blended.itestsupport.docker._
import de.woq.blended.itestsupport.docker.protocol._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TestContainerManager extends ContainerManager with DockerClientProvider {
  override def getClient = {
    implicit val logger = context.system.log
    DockerClientFactory(context.system.settings.config)
  }
}

trait BlendedIntegrationTestSupport { this: TestKit =>

  val system: ActorSystem
  private val mgrName = "ContainerManager"

  def startContainer(timeout : FiniteDuration) = {

    implicit val eCtxt = system.dispatcher

    System.setProperty("docker.io.version", "1.12")
    val mgr = system.actorOf(Props[TestContainerManager], mgrName)

    val call = (mgr ? StartContainerManager)(new Timeout(timeout))
    Await.result(call, timeout)
  }

  def containerMgr = {
    system.actorSelection(s"/user/${mgrName}").resolveOne(1.second).mapTo[ActorRef]
  }

  def jolokiaUrl = {
    implicit val eCtxt = system.dispatcher

    containerMgr.map { mgr =>
      (mgr ? GetContainerPorts("blended_demo_0"))(new Timeout(3.seconds)).mapTo[ContainerPorts].onComplete {
        case Success(ports) => ports
        case Failure(cause) => throw cause
      }
    }
  }

}