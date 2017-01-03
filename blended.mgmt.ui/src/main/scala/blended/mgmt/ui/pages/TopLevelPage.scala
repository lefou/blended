package blended.mgmt.ui.pages

import japgolly.scalajs.react.{ReactComponentB, ReactElement}
import japgolly.scalajs.react.extra.router.{Path, Redirect, RouterConfig, RouterConfigDsl}

abstract class TopLevelPage {

  val name : String

  val routerPath : Path

  val content : ReactElement

  def component = ReactComponentB.static(name, content).build
}

object TopLevelPages {

  val values : List[TopLevelPage] = List(ContainerPage, HelpPage)

  val defaultPage = values.head

  def routes = RouterConfigDsl[TopLevelPage].buildRule { dsl =>
    import dsl._

    (trimSlashes
    | staticRoute(root, TopLevelPages.defaultPage) ~> renderR(_ => ContainerPage.component())
    | staticRoute("#help", HelpPage) ~> renderR(_ => HelpPage.component())
    )

  }

}
