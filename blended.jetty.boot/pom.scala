import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedJettyBoot,
  packaging = "bundle",
  description = "Bundle wrapping the original jetty boot bundle to dynamically provide SSL Context via OSGI services.",
  dependencies = Seq(
    scalaLib % "provided",
    scalaReflect % "provided",
    blendedDomino,
    log4s,
    jettyOsgiBoot,
    logbackCore % "test",
    logbackClassic % "test",
    scalaTest % "test",
    blendedTestSupport % "test"
  ),
  plugins = Seq(
    Plugin(
      mavenBundlePlugin.gav,
      extensions = true,
      inherited = true,
      configuration = Config(
        instructions = new Config(Seq(
          "_include" -> Option("osgi.bnd"),
          "Embed-Dependency" -> Option(s"*;artifactId=${jettyOsgiBoot.artifactId}")
        ))
      )
    ),
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)