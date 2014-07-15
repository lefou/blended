package de.woq.blended.itestsupport.docker

import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import com.github.dockerjava.client.model.Ports.Binding
import com.github.dockerjava.client.model.{ExposedPort, Ports}

import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.Future

object ContainerActor {
  def apply(container: DockerContainer, portScanner: ActorRef) = new ContainerActor(container, portScanner)
}

class ContainerActor(container: DockerContainer, portScanner: ActorRef) extends Actor with ActorLogging {

  implicit val timeout = new Timeout(5.seconds)
  implicit val eCtxt   = context.dispatcher

  def stopped : Receive = LoggingReceive {
    case StartContainer(n) if container.containerName == n  => {
      portBindings
    }
    case p : Ports => {
      val requestor = sender
      container.startContainer(p).waitContainer
      context become started
      requestor ! ContainerStarted(container.containerName)
    }
  }

  def started : Receive = LoggingReceive {
    case StopContainer(n) if container.containerName == n  => {
      val requestor = sender
      container.stopContainer
      context become stopped
      requestor ! ContainerStopped(container.containerName)
    }
    case GetContainerPorts(n) if container.containerName == n => {
      val ports : Map[String, NamedContainerPort] =
        container.ports.mapValues { namedPort =>
          val exposedPort = new ExposedPort("tcp", namedPort.sourcePort)
          val mapped = container.exposedPorts.get.getBindings.get(exposedPort)
          val realPort = mapped.getHostPort
          NamedContainerPort(namedPort.name, realPort)
        }
      sender ! ContainerPorts(ports)
    }
  }

  def receive = stopped

  private def portBindings {
    val bindings = new Ports()

    // We create a Future for each port. The Future uses the underlying PortScanner
    // to retreive the next port number
    val portRequests : Iterable[Future[(NamedContainerPort, FreePort)]] =
      container.ports.values.map { case namedPort =>
        (portScanner ? GetPort).mapTo[FreePort].collect { case fp =>
          (namedPort, fp)
        }
    }

    // We create a single Future from the list of futures created before, collect the result
    // and then pass on the Bindings to ourselves.
    Future.sequence(portRequests).mapTo[Iterable[(NamedContainerPort, FreePort)]].collect { case ports =>
      ports.foreach { case (namedPort, freeport) =>
        bindings.bind(new ExposedPort("tcp", namedPort.sourcePort), new Binding(freeport.p))
      }
    } onSuccess { case _ => self ! bindings }
  }
}