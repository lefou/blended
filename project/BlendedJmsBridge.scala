import sbt._
import TestLogConfig.autoImport._

object BlendedJmsBridge extends ProjectFactory {

  private[this] val helper : ProjectSettings = new ProjectSettings(
    projectName = "blended.jms.bridge",
    description = "A generic JMS bridge to connect the local JMS broker to en external JMS",
    deps = Seq(
      Dependencies.typesafeConfig,

      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.scalatest % "test"
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / testlogLogPackages ++= Map("blended" -> "DEBUG")
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtil.project,
    BlendedUtilLogging.project,
    BlendedJmsUtils.project,
    BlendedDomino.project,
    BlendedAkka.project,
    BlendedStreams.project,

    BlendedActivemqBrokerstarter.project % "test",
    BlendedTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test",
    BlendedStreamsTestsupport.project % "test"
  )
}
