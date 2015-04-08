import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable._

implicit val scalaVersion = ScalaVersion("2.10.2")

ScalaModel(
  gav = "de.wayofquality.blended" % "blended-reactor" % "1.1.2-SNAPSHOT",
  modelVersion = "4.0.0",
  packaging = "pom",
  description = "A collection of bundles to develop OSGi application on top of Apache Karaf, Scala and Akka.",
  licenses = Seq(
    License(name = "The Apache License, Version 2.0", url = "http://www.apache.org/licenses/LICENSE-2.0.txt")
  ),
  organization= Organization(name = "WoQ - Way of Quality GmbH", url = "http://www.wayofquality.de"),
  developers = Seq(
    Developer(name = "Andreas Gies", email = "andreas@wayofquality.de",
              organization = "WoQ - Way of Quality GmbH",
              organizationUrl = "http://www.wayofquality.de")
  ),
  distributionManagement = DistributionManagement(
    repository = DeploymentRepository(id = "ossrh", url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"),
    snapshotRepository = DeploymentRepository(id = "ossrh", url = "https://oss.sonatype.org/content/repositories/snapshots/")
  ),
  scm = Scm(
    connection = "scm:git:ssh://git@github.com/woq-blended/blended.git",
    developerConnection= "scm:git:ssh://git@github.com/woq-blended/blended.git",
    url = "https://github.com/woq-blended/blended"
  ),
  profiles = Seq(
    Profile(id = "parent", modules = Seq("blended-parent", "blended-karaf-parent", "blended-karaf-branding")),
    Profile(id = "build", modules = Seq("blended-karaf-installer", "blended-activemq-brokerstarter", "blended-container-context", "blended-container-id", "blended-container-registry", "blended-util", "blended-jmx", "blended-camel-utils", "blended-testsupport", "blended-karaf-features", "blended-karaf-branding", "blended-modules", "blended-akka", "blended-mgmt-agent", "blended-mgmt-rest", "blended-spray-api", "blended-spray", "blended-neo4j-api", "blended-persistence", "blended-jolokia", "blended-itestsupport", "blended-samples")),
    Profile(id = "testing", modules = Seq("blended-testing")),
    Profile(id = "assembly", modules = Seq("blended-karaf-demo")),
    Profile(id = "itest", modules = Seq("blended-itestsupport", "blended-akka-itest")),
    Profile(id = "docker", modules = Seq("blended-docker"))
  )
)
