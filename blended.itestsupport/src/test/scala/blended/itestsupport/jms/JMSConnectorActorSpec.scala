/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.itestsupport.jms

import akka.actor.Props
import akka.pattern.ask
import akka.testkit.{TestProbe, TestActorRef}
import akka.util.Timeout
import blended.itestsupport.jms.protocol._
import blended.testsupport.TestActorSys
import blended.util.protocol.{CounterInfo, TrackingCounter}
import org.apache.activemq.ActiveMQConnectionFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class JMSConnectorActorSpec extends AbstractJMSSpec {

  "The JMSConnectorActor" should {

    "Allow to (dis)connect via the underlying JMS connection factory" in TestActorSys { testkit =>
      implicit val system = testkit.system

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      connect(connector)
      disconnect(connector)
    }

    "Respond with a caught exception if the connection can't be established" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val invalidCf = new ActiveMQConnectionFactory("vm://foo?create=false")
      val connector = TestActorRef(Props(JMSConnectorActor(invalidCf)))

      connector.tell(Connect("invalid"), probe.ref)

      probe.fishForMessage() {
        case Left(e: JMSCaughtException) => true
        case _ => false
      }
    }

    "Allow to create a producer" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      connect(connector)

      connector.tell(CreateProducer("queue:test"), probe.ref)
      probe.expectMsgAllClassOf(classOf[ProducerActor])

      disconnect(connector)
    }

    "Allow to create a consumer" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      implicit val timeout = Timeout(3.seconds)

      system.eventStream.subscribe(probe.ref, classOf[ConsumerStopped])

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      connect(connector)

      val consumer =
        Await.result((connector ? CreateConsumer("queue:test")).mapTo[ConsumerActor], 1.second)

      consumer.consumer ! StopConsumer
      probe.expectMsg(ConsumerStopped("queue:test"))

      disconnect(connector)
    }

    "Allow to create a durable subscriber" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      implicit val timeout = Timeout(3.seconds)

      system.eventStream.subscribe(probe.ref, classOf[ConsumerStopped])

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      connect(connector)

      val consumer =
        Await.result((connector ? CreateDurableSubscriber("topic:test", "test")).mapTo[ConsumerActor], 1.second)

      consumer.consumer ! StopConsumer
      probe.expectMsg(ConsumerStopped("topic:test"))

      disconnect(connector)
    }

    "Count the number of produced messages" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      implicit val timeout = Timeout(3.seconds)

      val NUM_MSG = new Random(System.currentTimeMillis).nextInt(50) + 50

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      val counter = TestActorRef(Props(TrackingCounter(1.second, probe.ref)))

      connect(connector)

      val producerActor =
        Await.result((connector ? CreateProducer("topic:test", Some(counter))).mapTo[ProducerActor], 1.second)

      producerActor.producer ! ProduceMessage(
        msgFactory = new TextMessageFactory,
        content = Some("test"),
        count = NUM_MSG
      )

      probe.fishForMessage() {
        case info: CounterInfo =>
          info.count == NUM_MSG
        case _ => false
      }

      disconnect(connector)
    }
  }
}