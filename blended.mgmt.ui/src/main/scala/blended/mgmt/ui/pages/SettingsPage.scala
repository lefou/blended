package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._

object SettingsPage extends TopLevelPage {

  override val name: String = "Settings"

  override val routerPath: Path = Path("#settings")

  override val content: ReactElement = <.p(
    "This is the settings Page"
  )
}
