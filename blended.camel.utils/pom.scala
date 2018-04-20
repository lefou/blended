import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.camelUtils,
  packaging = "bundle",
  description = """Useful helpers for Camel""",
  dependencies = Seq(
    scalaLib % "provided",
    orgOsgi,
    orgOsgiCompendium,
    camelJms,
    slf4j,
    Blended.akka
  ),
  plugins = Seq(
    sbtCompilerPlugin,
    mavenBundlePlugin
  )
)
