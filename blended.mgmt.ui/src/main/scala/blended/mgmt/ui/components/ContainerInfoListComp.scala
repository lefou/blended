package blended.mgmt.ui.components

import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.{ Callback, ReactComponentB, ReactEventI }
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.BackendScope
import blended.updater.config.Profile
import blended.updater.config.OverlayState

/**
 * React Component to render a list of [[ContainerInfo]]s.
 */
object ContainerInfoListComp {

  private[this] val log = Logger[ContainerInfoListComp.type]
  private[this] val i18n = I18n()

  case class Props(containerInfos: List[ContainerInfo], selectContainer: Option[ContainerInfo] => Unit)

  class Backend(scope: BackendScope[Props, Unit]) {

    def selectContainerInfo(containerInfo: Option[ContainerInfo]): Callback = {
      scope.props.map(p => p.selectContainer(containerInfo))
    }

    def render(p: Props) = {
      val rows = p.containerInfos.map { ci =>
        val singleProfiles = ci.profiles.flatMap(_.toSingle)
        val activeProfile = singleProfiles.find(p => p.state == OverlayState.Active)

        <.div(
          ^.onClick --> selectContainerInfo(Some(ci)),
          ci.containerId,
          activeProfile.isDefined ?= <.span(
            " (",
            activeProfile.get.name,
            "-",
            activeProfile.get.version,
            !activeProfile.get.overlaySet.overlays.isEmpty ?=
              i18n.tr(" with {0}", activeProfile.get.overlaySet.overlays.mkString(", ")),
            ")"
          )
        )

      }

      <.div(rows)
    }
  }

  val Component =
    ReactComponentB[Props]("ContainerInfoList")
      .renderBackend[Backend]
      .build

}