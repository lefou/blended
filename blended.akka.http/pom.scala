import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedAkkaHttp,
  packaging = "bundle",
  description = "Provide Akka HTTP support",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.scalaReflect % "provided",
    blendedContainerContextApi,
    blendedDomino,
    blendedUtil,
    Deps.domino,
    Deps.orgOsgi,
    Deps.akkaStream,
    Deps.akkaOsgi,
    blendedAkka,
    Deps.akkaHttp,
    Deps.log4s,
    blendedTestSupportPojosr % "test",
    Deps.akkaTestkit % "test",
    Deps.akkaSlf4j % "test",
    Deps.mockitoAll % "test",
    Deps.scalaTest % "test",
    Deps.akkaHttpTestkit % "test",
    Deps.logbackCore % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
