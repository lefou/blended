import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-common.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala

BlendedModel(
  gav = blendedLauncher,
  packaging = "bundle",
  description = "Provide an OSGi Launcher",
  dependencies = Seq(
    scalaLib,
    orgOsgi,
    slf4j,
    typesafeConfig,
    commonsDaemon,
    blendedUpdaterConfig,
    cmdOption,
    logbackClassic % "runtime",
    scalaTest % "test",
    felixFramework % "test",
    felixGogoRuntime % "test",
    felixGogoShell % "test",
    felixGogoCommand % "test",
    felixFileinstall % "test",
    felixMetatype % "test"
  ),
  properties = Map(
    "blended.launcher.version" -> blendedLauncher.version.get,
    "blended.updater.config.version" -> blendedUpdaterConfig.version.get,
    "cmdoption.version" -> cmdOption.version.get,
    "org.osgi.core.version" -> orgOsgi.version.get,
    "scala.library.version" -> scalaLib.version.get,
    "typesafe.config.version" -> typesafeConfig.version.get,
    "slf4j.version" -> slf4j.version.get,
    "logback.version" -> logbackClassic.version.get
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin,
    Plugin(
      "org.apache.maven.plugins" % "maven-resources-plugin" % "3.0.1",
      executions = Seq(
        Execution(
          id = "runner-resources",
          phase = "process-resources",
          goals = Seq("copy-resources"),
          configuration = Config(
            outputDirectory = "${basedir}/target/runner-resources",
            resources = Config(
              resource = Config(
                directory = "src/runner/resources",
                filtering = true
              ),
              resource = Config(
                directory = "src/runner/binaryResources",
                filtering = false
              )
            ),
            delimiters = Config(
              delimiter = "@"
            )
          )
        )
      )
    ),
    Plugin(
      "org.apache.maven.plugins" % "maven-assembly-plugin",
      executions = Seq(
        Execution(
          id = "bin",
          phase = "package",
          goals = Seq(
            "single"
          )
        )
      ),
      configuration = Config(
        descriptors = Config(
          descriptor = "src/main/assembly/bin.xml"
        )
      )
    )
  )
)