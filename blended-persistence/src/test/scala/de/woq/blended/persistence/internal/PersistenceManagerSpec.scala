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

package de.woq.blended.persistence.internal

import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{BeforeAndAfterAll, WordSpecLike, Matchers}
import akka.event.Logging.Info
import de.woq.blended.persistence.internal.person.{PersonCreator, Person}
import de.woq.blended.akka.protocol._
import akka.actor.{ActorRef, Props, PoisonPill}
import org.scalatest.mock.MockitoSugar
import de.woq.blended.akka.internal.OSGIFacade
import de.woq.blended.akka.{OSGIActor, BlendedAkkaConstants}
import scala.concurrent.duration._
import de.woq.blended.persistence.protocol._
import de.woq.blended.persistence.protocol.QueryResult
import de.woq.blended.persistence.protocol.StoreObject
import de.woq.blended.persistence.protocol.FindObjectByID

class PersistenceManagerSpec
  extends TestActorSys
  with WordSpecLike
  with Matchers
  with PersistenceBundleName
  with TestSetup
  with MockitoSugar
  with BeforeAndAfterAll {

  var facade : ActorRef = _
  var pMgr : ActorRef = _
  var dataCreator : ActorRef = _

  override protected def beforeAll() {
    facade = system.actorOf(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)
    pMgr = system.actorOf(Props(PersistenceManager(new Neo4jBackend())), bundleSymbolicName)
    pMgr ! InitializeBundle(osgiContext)

    dataCreator = system.actorOf(Props(new DataObjectCreator(new PersonCreator()) with OSGIActor))
    dataCreator ! BundleActorStarted(bundleSymbolicName)
  }

  override protected def afterAll() {
    pMgr ! PoisonPill
  }

  "The PersistenceManager" should {

    "Initialize correctly" in {

      system.eventStream.subscribe(self,classOf[Info])

      fishForMessage(10.seconds) {
        case Info(_, _, m : String) => m.startsWith("Initializing embedded Neo4j with path")
        case _ => false
      }
    }

    "Store a data object correctly" in {
      val info = new Person(firstName = "Andreas", name = "Gies")
      pMgr ! StoreObject(info)

      fishForMessage(10.seconds) {
        case QueryResult(List(info)) => true
        case _ => false
      }
    }

    "Retrieve a data object by its uuid" in {
      val info = new Person(firstName = "Andreas", name = "Gies")
      pMgr ! StoreObject(info)

      fishForMessage(10.seconds) {
        case QueryResult(List(info)) => true
        case _ => false
      }

      pMgr ! FindObjectByID(info.objectId, info.persistenceType)

      fishForMessage(10.seconds) {
        case QueryResult(List(person)) => person == info
        case _ => {
          false
        }
      }
    }
  }

}
