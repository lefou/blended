import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityAkkaHttp,
  packaging = "bundle",
  description = "Some security aware Akka HTTP routes for the blended container.",
  dependencies = Seq(
    Deps.scalaLib,
    Blended.akka,
    Blended.security,
    Blended.utilLogging,
    Deps.akkaHttp,
    Deps.akkaStream,
    Deps.orgOsgi,
    Deps.orgOsgiCompendium,
    Deps.slf4j,
    Blended.testSupport % "test",
    Deps.commonsBeanUtils % "test",
    Deps.scalaTest % "test",
    Deps.akkaHttpTestkit % "test",
    Deps.jclOverSlf4j % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
