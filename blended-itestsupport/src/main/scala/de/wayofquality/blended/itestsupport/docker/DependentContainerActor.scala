/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.wayofquality.blended.itestsupport.docker

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import de.wayofquality.blended.itestsupport.docker.protocol._
import de.wayofquality.blended.itestsupport.{ContainerLink, ContainerUnderTest}

/**
 * This is a helper Actor that works on behalf of the #ContainerManager to delay
 * the start of a container until all linked containers have been started.
 *
 * One instance of this actor is created for every defined container that has
 * at least one container link defined. The ContainerManager propagates the
 * ContainerStarted events. These events will be used to clear the list of
 * containers that we are waiting for. Once the list is Empty we will send
 * an DependenciesStarted message to the ContainerManager, so the he can start
 * the container afterwards.
 */
class DependentContainerActor(container: ContainerUnderTest) extends Actor with ActorLogging {

  def receive : Receive = waiting(container.links.map(_.container), container.links)

  def waiting(pendingContainers : List[String], links: List[ContainerLink]) : Receive = LoggingReceive {
    case ContainerStarted(ct) => ct match {
      case Right(cut) =>
        
        val newLinks = links.map { l => 
          if (l.container == cut.ctName) 
            ContainerLink(cut.dockerName, l.hostname)
          else 
            l
        }
        
        pendingContainers.filter(_ != cut.ctName) match {
          case l if l.isEmpty =>
            val newCut = container.copy(links = newLinks)
            log info s"Dependencies for container [$newCut] started."
            sender ! DependenciesStarted(Right(newCut))
            context.stop(self)
          case l => 
            log.debug(s"$pendingContainers")
            context.become(waiting(l, newLinks))
        }
      case Left(e) => 
        sender ! DependenciesStarted(Left(e))
        context.stop(self)
      }
  }
}

object DependentContainerActor {
  def apply(container : ContainerUnderTest) = new DependentContainerActor(container)
}
