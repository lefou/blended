import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.mgmtRepoRest,
  packaging = "bundle",
  description = "File Artifact Repository REST Service",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.domino,
    Blended.updaterConfig,
    Blended.mgmtBase,
    Blended.mgmtRepo,
    Blended.securityAkkaHttp,
    Blended.akkaHttp,
    Deps.akkaHttp,
    Blended.util,
    Deps.log4s,
    sprayJson,
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
