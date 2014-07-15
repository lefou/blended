package de.woq.blended.itestsupport.docker

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

class DependentContainerActorSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  "the DependentContainerActor" should {

    "Initialize itself with the links from the container config" in {
      val container = new DockerContainer(imageId, ctName).withLink("blended_demo_0")

      val depActor = TestActorRef(Props(DependentContainerActor(container)))
      val realActor = depActor.underlyingActor.asInstanceOf[DependentContainerActor]
      realActor.pendingContainers should contain theSameElementsAs Vector("blended_demo_0")
    }

    "Ignore Container starts that are not of any interest" in {
      val container = new DockerContainer(imageId, ctName).withLink("blended_demo_0")
      val depActor = TestActorRef(Props(DependentContainerActor(container)))
      depActor ! ContainerStarted("foo")
      val realActor = depActor.underlyingActor.asInstanceOf[DependentContainerActor]
      realActor.pendingContainers should contain theSameElementsAs Vector("blended_demo_0")
    }

    "Respond with a DependenciesStarted message after the last dependant container was started" in {
      val container = new DockerContainer(imageId, ctName).withLink("blended_demo_0")
      val depActor = TestActorRef(Props(DependentContainerActor(container)))
      depActor ! ContainerStarted("blended_demo_0")
      val realActor = depActor.underlyingActor.asInstanceOf[DependentContainerActor]
      realActor.pendingContainers.size should be (0)

      expectMsg(DependenciesStarted(container))
    }

  }

}