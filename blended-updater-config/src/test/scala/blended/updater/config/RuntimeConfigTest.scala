package blended.updater.config

import org.scalatest.FreeSpecLike

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory

class RuntimeConfigTest extends FreeSpecLike {

  "Minimal config" - {

    val minimal = """
      |name = name
      |version = 1.0.0
      |bundles = [{ url = "http://example.org", jarName = "bundle1.jar", sha1Sum = sum, startLevel = 0 }]
      |startLevel = 10
      |defaultStartLevel = 10
      |""".stripMargin

    "read" in {
      val config = RuntimeConfig.read(ConfigFactory.parseString(minimal)).get
    }

    val lines = minimal.trim().split("\n")
    0.to(lines.size - 1).foreach { n =>
      "without line " + n + " must fail" in {
        val config = lines.take(n) ++ lines.drop(n + 1)
        val ex = intercept[RuntimeException] {
          RuntimeConfig.read(ConfigFactory.parseString(config.mkString("\n"))).get
        }
        assert(ex.isInstanceOf[ConfigException.ValidationFailed] || ex.isInstanceOf[IllegalArgumentException])
      }
    }

    "read -> toConfig -> read must result in same config" in {
      import RuntimeConfig._
      val config = read(ConfigFactory.parseString(minimal))
      assert(config === read(toConfig(config.get)))
    }
  }

}