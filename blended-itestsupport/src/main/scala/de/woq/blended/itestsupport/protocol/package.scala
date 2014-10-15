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

import de.woq.blended.itestsupport.condition.{AsyncCondition, Condition}

package object protocol {

  case object ResetPortRange
  case object GetPort
  case class FreePort(p: Int)

  // Use this object to query an actor that encapsulates a condition.
  case object CheckCondition

  // Use this object to kick off an Asynchronous checker
  case class CheckAsyncCondition(condition: AsyncCondition)

  object ConditionCheckResult {
    def apply(results: List[ConditionCheckResult]) = {
      new ConditionCheckResult(
        results.map { r => r.satisfied}.flatten,
        results.map { r => r.timedOut}.flatten
      )
    }
  }
  case class ConditionCheckResult(satisfied: List[Condition], timedOut: List[Condition]) {
    def allSatisfied = timedOut.isEmpty

    def reportTimeouts : String =
      timedOut.mkString(
        s"\nA total of [${timedOut.size}] conditions have timed out", "\n", ""
      )
  }

}
