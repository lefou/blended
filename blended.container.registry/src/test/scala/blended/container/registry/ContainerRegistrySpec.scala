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

package blended.container.registry.internal

import akka.testkit.{TestActorRef, TestProbe}
import blended.akka.OSGIActorConfig
import blended.container.context.{ContainerContext, ContainerIdentifierService}
import blended.testsupport.TestActorSys
import blended.updater.config.{ContainerInfo, ContainerRegistryResponseOK, UpdateContainerInfo}
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito.when
import org.osgi.framework.{Bundle, BundleContext}
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar

class ContainerRegistrySpec extends WordSpec with MockitoSugar with Matchers {

  val osgiContext = mock[BundleContext]
  val idSvc = mock[ContainerIdentifierService]
  val ctContext = mock[ContainerContext]
  val bundle = mock[Bundle]

  when(idSvc.getContainerContext()) thenReturn(ctContext)
  when(ctContext.getContainerConfigDirectory) thenReturn ("./target/test-classes")
  when(osgiContext.getBundle()) thenReturn(bundle)
  when(bundle.getBundleContext) thenReturn(osgiContext)
  when(bundle.getSymbolicName) thenReturn("foo")

  "Container registry" should {

    "Respond with a OK message upon an container update message" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val probe = TestProbe()

      val registry = TestActorRef(ContainerRegistryImpl.props(OSGIActorConfig(osgiContext, system, ConfigFactory.empty(), idSvc)))
      val profiles = List()
      registry.tell(UpdateContainerInfo(ContainerInfo("foo", Map("name" -> "andreas"), serviceInfos = List(), profiles)), probe.ref)

      probe.expectMsg(ContainerRegistryResponseOK("foo"))
    }
  }

}