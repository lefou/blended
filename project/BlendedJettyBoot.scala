import com.typesafe.sbt.osgi.OsgiKeys
import sbt.Keys._
import sbt._
import blended.sbt.Dependencies

object BlendedJettyBoot extends ProjectFactory {

  private[this] val helper: ProjectSettings = new ProjectSettings(
    projectName = "blended.jetty.boot",
    description = "Bundle wrapping the original jetty boot bundle to dynamically provide SSL Context via OSGI services",
    deps = Seq(
      Dependencies.domino,
      Dependencies.jettyOsgiBoot
    )
  ) {

    private val jettyVersion = "version=\"[9.4,20)\""

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.JettyActivator",
      importPackage = Seq(
        s"org.eclipse.jetty.annotations;${jettyVersion};resolution:=optional",
        s"org.eclipse.jetty.deploy;${jettyVersion}",
        s"org.eclipse.jetty.deploy.bindings;${jettyVersion}",
        s"org.eclipse.jetty.deploy.graph;${jettyVersion}",
        s"org.eclipse.jetty.http;${jettyVersion}",
        s"org.eclipse.jetty.server;${jettyVersion}",
        s"org.eclipse.jetty.server.handler;${jettyVersion}",
        s"org.eclipse.jetty.util;${jettyVersion}",
        s"org.eclipse.jetty.util.thread;${jettyVersion}",
        s"org.eclipse.jetty.util.component;${jettyVersion}",
        s"org.eclipse.jetty.util.log;${jettyVersion}",
        s"org.eclipse.jetty.util.resource;${jettyVersion}",
        s"org.eclipse.jetty.webapp;${jettyVersion}",
        s"org.eclipse.jetty.xml;${jettyVersion}",
        s"org.osgi.service.event",
        s"javax.mail.*;resolution:=optional",
        s"javax.transaction.*;resolution:=optional",
        s"org.objectweb.asm;resolution:=optional",
        s"org.osgi.service.cm",
        s"org.osgi.service.url",
        s"org.slf4j.*;resolution:=optional",
        s"org.xml.sax,org.xml.sax.helpers"
      ),
      additionalHeaders = Map(
        "DynamicImport-Package" -> s"org.eclipse.jetty.*;${jettyVersion}"
      )
    )


    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      OsgiKeys.embeddedJars := {
        val jettyOsgi = BuildHelper.resolveModuleFile(Dependencies.jettyOsgiBoot.intransitive(), target.value)
        jettyOsgi.distinct
      }
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedUtilLogging.project
  )
}
