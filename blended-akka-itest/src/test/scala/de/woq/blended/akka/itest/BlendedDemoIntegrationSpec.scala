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

package de.woq.blended.akka.itest

import javax.jms.ConnectionFactory

import akka.actor.Props
import de.woq.blended.itestsupport.condition.{ParallelComposedCondition, SequentialComposedCondition, AsyncCondition}
import de.woq.blended.itestsupport.docker.protocol.ContainerManagerStarted
import de.woq.blended.itestsupport.jms.{JMSAvailableCondition, JMSChecker}
import de.woq.blended.itestsupport.jolokia.{JolokiaAvailableCondition, JolokiaAvailableChecker}
import de.woq.blended.itestsupport.{BlendedIntegrationTestSupport, BlendedTestContext}
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.SpecLike

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Await
import scala.concurrent.duration._

object BlendedDemoIntegrationSpec {
  val amqConnectionFactory = "amqConnectionFactory"
  val jmxRest = "jmxRest"
}

class BlendedDemoIntegrationSpec extends TestActorSys
  with SpecLike
  with BlendedIntegrationTestSupport {

  override def nestedSuites = IndexedSeq(new BlendedDemoSpec)

  override def preCondition = {
    val t = 30.seconds

    ParallelComposedCondition(
      JolokiaAvailableCondition(jmxRest, Some(t), Some("blended"), Some("blended")),
      JMSAvailableCondition(amqConnectionFactory, Some(t))
    )

//    new SequentialComposedCondition(
//      new ParallelComposedCondition(
//        //new JolokiaAvailableCondition(jmxRest, t, Some("blended"), Some("blended")),
//        new JMSAvailableCondition(amqConnectionFactory, t)
//      )
////      new MbeanExistsCondition(jmxRest, t, Some("blended"), Some("blended")) with MBeanSearchDef {
////        override def jmxDomain = "org.apache.camel"
////
////        override def searchProperties = Map(
////          "name" -> "\"BlendedSample\"",
////          "type" -> "context"
////        )
////      }
//    )
  }

  private lazy val jmxRest = {
    val url = Await.result(jolokiaUrl(ctName = "blended_demo_0", port = 8181), 3.seconds)
    url should not be (None)
    BlendedTestContext.set(BlendedDemoIntegrationSpec.jmxRest, url.get).asInstanceOf[String]
  }

  private lazy val amqConnectionFactory = {
    val ctInfo = Await.result(containerInfo("blended_demo_0"), 3.seconds)
    val address = ctInfo.getNetworkSettings.getIpAddress
    BlendedTestContext.set(
      BlendedDemoIntegrationSpec.amqConnectionFactory,
      new ActiveMQConnectionFactory(s"tcp://${address}:1883")
    ).asInstanceOf[ConnectionFactory]
  }
}
