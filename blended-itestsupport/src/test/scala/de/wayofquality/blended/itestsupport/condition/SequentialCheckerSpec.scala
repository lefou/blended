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
import de.wayofquality.blended.itestsupport.protocol._
import de.wayofquality.blended.testsupport.TestActorSys

import org.scalatest.{DoNotDiscover, Matchers, WordSpecLike}

class SequentialCheckerSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "The Condition Checker" should {

    "respond with a satisfied message on an empty list of conditions" in {

      val condition = new SequentialComposedCondition()
      val checker = TestActorRef(Props(ConditionActor(condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(List.empty[Condition], List.empty[Condition]))
    }

    "respond with a satisfied message after a single wrapped condition has been satisfied" in {
      val conditions = (1 to 1).map { i => new AlwaysTrue() }.toList
      val condition = new SequentialComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(conditions, List.empty[Condition]))
    }

    "respond with a satisfied message after some wrapped conditions have been satisfied" in {
      val conditions = (1 to 5).map { i => new AlwaysTrue() }.toList
      val condition = new SequentialComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(conditions, List.empty[Condition]))
    }

    "respond with a timeout message after a single wrapped condition has timed out" in {
      val conditions = (1 to 1).map { i => new NeverTrue() }.toList
      val condition = new SequentialComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(List.empty[Condition], conditions))
    }

    "respond with a timeout message after the first wrapped condition has timed out" in {
      val conditions = (1 to 5).map { i => new NeverTrue() }.toList
      val condition = new SequentialComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(List.empty[Condition], conditions))
    }

    """"
     respond with a timeout message containing the remaining Conditions
     after the first failing condition has timed out
    """ in {

      val trueConditions      = (1 to 2).map { i => new AlwaysTrue() }.toList
      val remainingConditions = new NeverTrue() :: (1 to 2).map { i => new AlwaysTrue() }.toList

      val condition = new SequentialComposedCondition((trueConditions ::: remainingConditions).toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(trueConditions, remainingConditions))
    }
  }

}