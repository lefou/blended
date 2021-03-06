import sbt._
import blended.sbt.Dependencies

object BlendedMgmtRepo extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.mgmt.repo",
    description = "File Artifact Repository",
    deps = Seq(
      Dependencies.scalatest % "test",
      Dependencies.lambdaTest % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.scalatest % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.ArtifactRepoActivator",
      privatePackage = Seq(
        s"${b.bundleSymbolicName}.file.*",
        s"${b.bundleSymbolicName}.internal.*"
      )
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedUpdaterConfigJvm.project,
    BlendedUtilLogging.project,
    BlendedMgmtBase.project,
    BlendedTestsupport.project % "test"
  )
}
