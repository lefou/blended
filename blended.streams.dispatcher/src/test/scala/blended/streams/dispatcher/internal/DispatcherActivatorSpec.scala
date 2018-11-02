package blended.streams.dispatcher.internal

import java.io.File

import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.bridge.internal.BridgeActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class DispatcherActivatorSpec extends LoggingFreeSpec
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] def withDispatcher[T](f : BlendedPojoRegistry => () => T) : T = {

    System.setProperty("AppCountry", "cc")
    System.setProperty("AppLocation", "09999")

    withSimpleBlendedContainer(new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()){sr =>
      withStartedBundles(sr)(Seq(
        "blended.akka" -> Some(() => new BlendedAkkaActivator()),
        "blended.activemq.brokerstarter" -> Some(() => new BrokerActivator()),
        "blended.jms.bridge" -> Some(() => new BridgeActivator())
      )) { sr =>
        f(sr)()
      }
    }
  }

  "The activated dispatcher should" - {

    "create the dispatcher" in {

      withDispatcher { sr => () =>

        pending
      }
    }
  }
}
