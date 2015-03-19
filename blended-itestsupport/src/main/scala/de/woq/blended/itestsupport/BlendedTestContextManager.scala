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

package de.woq.blended.itestsupport

import akka.actor.Actor
import akka.actor.ActorLogging
import de.woq.blended.akka.MemoryStash
import akka.event.LoggingReceive
import akka.actor.PoisonPill
import de.woq.blended.itestsupport.protocol.TestContextRequest
import akka.camel.CamelExtension
import org.apache.camel.CamelContext

class BlendedTestContextManager extends Actor with ActorLogging with MemoryStash { this : TestContextConfigurator =>
  
  val camel = CamelExtension(context.system)
  
  def initializing : Receive = LoggingReceive {
    case req : TestContextRequest => 
      val camelCtxt = configure(req.cuts, camel.context)
      context.become(working(camelCtxt))
      sender ! camelCtxt
      unstash()
  } 
  
  def working(testContext: CamelContext) = LoggingReceive {
    case m => log info s"$m"
  } 
    
  def receive = initializing orElse stashing
}
