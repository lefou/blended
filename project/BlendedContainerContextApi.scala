import sbt._
import blended.sbt.Dependencies

object BlendedContainerContextApi extends ProjectFactory {

  private[this] val helper: ProjectSettings = new ProjectSettings(
    projectName = "blended.container.context.api",
    description = "The API for the Container Context and Identifier Services",
    deps = Seq(
      Dependencies.springExpression,
      Dependencies.typesafeConfig,
      Dependencies.springCore % "test",
      Dependencies.jclOverSlf4j % "test",
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      importPackage = Seq(
        "blended.launcher.runtime;resolution:=optional"
      )
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project
  )

}
