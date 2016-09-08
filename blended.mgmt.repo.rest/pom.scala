import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-common.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala

BlendedModel(
  gav = blendedMgmtRepoRest,
  packaging = "bundle",
  description = "File Artifact Repository REST Service",
  dependencies = Seq(
    scalaLib % "provided",
    blendedDomino,
    blendedUpdaterConfig,
    blendedMgmtBase,
    blendedMgmtRepo,
    blendedSpray,
    sprayJson,
    scalaTest % "test"
  ), 
  plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin,
      scalatestMavenPlugin
  )
)
