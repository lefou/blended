/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.java.testsupport

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import java.util.concurrent.atomic.AtomicInteger

object TestActorSys {
  val uniqueId = new AtomicInteger(0)
}

class TestActorSys(name : String)
  extends TestKit(ActorSystem(name))
  with ImplicitSender {

  def this() = this("TestSystem%05d".format(TestActorSys.uniqueId.incrementAndGet()))

  def shutdown() { system.shutdown() }

  def apply(block : Unit) {
    try block
    finally shutdown()
  }
}