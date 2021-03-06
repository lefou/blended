import sbt._
import blended.sbt.Dependencies

object BlendedStreamsTestsupport extends ProjectFactory {

  private val settings = new ProjectSettings(
    projectName = "blended.streams.testsupport",
    description = "Some classes to make testing for streams a bit easier",
    osgi = false,
    deps = Seq(
      Dependencies.scalacheck,
      Dependencies.scalatest,
      Dependencies.akkaTestkit,
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.akkaPersistence,
      Dependencies.logbackCore,
      Dependencies.logbackClassic
    )
  )

  override val project = settings.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedStreams.project,
    BlendedTestsupport.project
  )
}
