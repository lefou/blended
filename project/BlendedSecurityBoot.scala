object BlendedSecurityBoot extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.security.boot",
    "A delegating Login Module for the Blended Container."
  ) {

    override def libDeps = Seq(
      Dependencies.orgOsgi
    )

    override def bundle: BlendedBundle = defaultBundle.copy(
      additionalHeaders = Map("Fragment-Host" -> "system.bundle;extension:=framework")
    )
  }

  override  val project = helper.baseProject
}
