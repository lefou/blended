/*
 * Copyright 2014ff,  https://github.com/woq-blended
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package blended.itestsupport.jolokia

import akka.actor.{ActorSystem, Props}
import blended.itestsupport.condition.AsyncCondition
import blended.jolokia.model.JolokiaSearchResult
import blended.jolokia.protocol._

import scala.concurrent.duration.FiniteDuration

object MBeanExistsCondition {

  def apply(
    url: String,
    user: Option[String] = None,
    pwd: Option[String] = None,
    searchDef: MBeanSearchDef,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem) =
    AsyncCondition(
      Props(MBeanExistsChecker(url, user, pwd, searchDef)),
      s"MBeanExistsCondition(${url}, ${searchDef.pattern}})",
      t
    )
}

object CamelContextExistsCondition {
  def apply(
    url: String,
    user: Option[String] = None,
    pwd: Option[String] = None,
    contextName : String,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem) = MBeanExistsCondition(
    url,
    user,
    pwd, new MBeanSearchDef {
      override def jmxDomain = "org.apache.camel"
      override def searchProperties = Map(
        "type" -> "context",
        "name" -> s""""${contextName}""""
      )
    },
    t
  )
}

object JmsBrokerExistsCondition {
  def apply(
    url: String,
    user: Option[String] = None,
    pwd: Option[String] = None,
    brokerName : String,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem) = MBeanExistsCondition(
    url,
    user,
    pwd, new MBeanSearchDef {
      override def jmxDomain = "org.apache.activemq"
      override def searchProperties = Map(
        "type" -> "Broker",
        "brokerName" -> s"${brokerName}"
      )
    },
    t
  )
}

private[jolokia] object MBeanExistsChecker {
  def apply(
    url: String,
    user: Option[String] = None,
    pwd: Option[String] = None,
    searchDef: MBeanSearchDef
  )(implicit system: ActorSystem) = new MBeanExistsChecker(url, user, pwd, searchDef)
}

private[jolokia] class MBeanExistsChecker(
  url: String,
  userName: Option[String] = None,
  userPwd: Option[String] = None,
  searchDef : MBeanSearchDef
)(implicit system:ActorSystem) extends JolokiaChecker(url, userName, userPwd) with JolokiaAssertion {

  override def toString = s"MbeanExistsCondition(${url}, ${searchDef.pattern}})"

  override def jolokiaRequest = SearchJolokia(searchDef)

  override def assertJolokia = { msg =>
    msg match {
      case v : JolokiaSearchResult => !v.mbeanNames.isEmpty
      case _ => false
    }
  }
}