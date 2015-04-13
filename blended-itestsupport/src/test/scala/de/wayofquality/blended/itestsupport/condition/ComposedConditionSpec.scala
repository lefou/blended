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

package de.wayofquality.blended.itestsupport.condition

import akka.actor.Props
import akka.testkit.TestActorRef
import de.wayofquality.blended.testsupport.TestActorSys
import org.scalatest.{DoNotDiscover, Matchers, WordSpecLike}
import scala.concurrent.duration._
import ConditionProvider._

import de.wayofquality.blended.itestsupport.protocol._

class ComposedConditionSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "A composed condition" should {

    val timeout = 2.seconds

    "be satisfied with an empty condition list" in {
      val condition = new ParallelComposedCondition()

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition
      expectMsg(ConditionCheckResult(List.empty, List.empty))
    }

    "be satisfied with a list of conditions that eventually satisfy" in {

      val conditions = List(alwaysTrue(), alwaysTrue(), alwaysTrue(), alwaysTrue())
      val condition = new ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition
      expectMsg(ConditionCheckResult(conditions, List.empty[Condition]))
    }

    "timeout with at least failing condition" in {
      val conditions = List(alwaysTrue(), alwaysTrue(), neverTrue(), alwaysTrue())
      val condition = new ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(
        conditions.filter(_.isInstanceOf[AlwaysTrue]),
        conditions.filter(_.isInstanceOf[NeverTrue])
      ))
    }
  }
}