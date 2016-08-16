import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedItestSupport,
  packaging = "jar",
  description = """Define an integration test API for collaborating blended container(s) using docker as a runtime 
    for the container(s) under test and an Akka based Camel framework to perform the integration tests 
    as pure blackbox tests. Container(s) may be prestarted and discovered (for execution speed) or 
    started by the integration test (for reproducability).""",
  dependencies = Seq(
    scalaLib % "provided",
    blendedJolokia,
    blendedTestSupport,
    "com.github.docker-java" % "docker-java" % dockerJavaVersion,
    akkaCamel,
    typesafeConfig,
    junit,
    commonsExec,
    camelCore,
    camelJms,
    camelHttp,
    slf4j,
    geronimoJms11Spec,
    akkaSlf4j % "test",
    slf4jLog4j12 % "test",
    mockitoAll % "test",
    activeMqBroker % "test",
    activeMqKahadbStore % "test",
    Dependency(
      "org.jolokia" % "jolokia-jvm" % jolokiaVersion,
      classifier = "agent",
      scope = "runtime"
    )
  ),
  pluginManagement = Seq(
      scalatestMavenPlugin
  ),
  plugins = Seq(
    Plugin(
      "org.apache.maven.plugins" % "maven-dependency-plugin",
      executions = Seq(
        Execution(
          id = "extract-blended-container",
          phase = "process-resources",
          goals = Seq(
            "copy-dependencies"
          ),
          configuration = Config(
            includeClassifiers = "agent",
            outputDirectory = "${project.build.directory}/jolokia"
          )
        )
      )
    ),
    scalaMavenPlugin,
    Plugin(
      scalatestMavenPlugin.gav,
      configuration = Config(
        argLine = "-javaagent:${project.build.directory}/jolokia/jolokia-jvm-" + jolokiaVersion + "-agent.jar=port=7777,host=localhost"
      )
    )
  )
)
