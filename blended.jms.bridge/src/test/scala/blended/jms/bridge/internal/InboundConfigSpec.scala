package blended.jms.bridge.internal

import java.io.File

import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import blended.jms.utils.{JmsDestination, JmsDurableTopic, JmsQueue, JmsTopic}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{MockContainerContext, PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers

class InboundConfigSpec extends LoggingFreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers {

  "The inbound config should" - {

    System.setProperty("BlendedCountry", "de")
    System.setProperty("BlendedLocation", "09999")

    val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()
    val idSvc = new ContainerIdentifierServiceImpl(new MockContainerContext(baseDir))

    "initialize from a plain config correctly" in {

      val cfgString =
        """
          |{
          |  name = "test"
          |  vendor = "activemq"
          |  from = "inQueue"
          |  to = "topic:out"
          |  listener = 4
          |}
        """.stripMargin

      val cfg = ConfigFactory.parseString(cfgString)

      val inbound = InboundConfig.create(idSvc, cfg).get

      inbound.name should be ("test")
      inbound.from should be (JmsQueue("inQueue"))
      inbound.to should be (JmsTopic("out"))
      inbound.provider should be (empty)
      inbound.listener should be (4)
    }

    "initialize from a config with placeholders correctly" in {

      val cfgString =
        """
          |{
          |  name = "test"
          |  vendor = "sonic"
          |  provider = "$[[BlendedCountry]]_topic"
          |  from = "topic:$[[BlendedCountry]]$[[BlendedLocation]]:$[[BlendedCountry]].$[[BlendedLocation]].data.in"
          |  to = "bridge.data.in"
          |  listener = 4
          |}
        """.stripMargin

      val cfg = ConfigFactory.parseString(cfgString)

      val inbound = InboundConfig.create(idSvc, cfg).get

      inbound.name should be ("test")
      inbound.from should be (JmsDurableTopic("de.09999.data.in", "de09999"))
      inbound.to should be (JmsQueue("bridge.data.in"))
      inbound.provider should be (Some("de_topic"))
      inbound.listener should be (4)

    }
  }


}
