import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedMgmtRest,
  packaging = "bundle",
  description = "REST interface to accept POST's from distributed containers. These will be delegated to the container registry.",
  dependencies = Seq(
    scalaLib % "provided",
    slf4j % "provided",
    blendedMgmtBase,
    blendedSpray,
    blendedSprayApi,
    blendedContainerRegistry,
    blendedAkka,
    blendedUpdaterRemote,
    orgOsgi,
    orgOsgiCompendium,
    scalaTest % "test",
    sprayTestkit % "test",
    mockitoAll,
    slf4jLog4j12
  ),
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin
  )
)