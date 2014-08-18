/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
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

package de.woq.blended.samples.spray.helloworld.internal

import de.woq.blended.akka.{ActorSystemAware, BundleName}
import akka.actor.Props

trait HelloBundleName extends BundleName {
  def bundleSymbolicName = "de.woq.blended.samples.spray.helloworld"
}

class HelloActivator extends ActorSystemAware with HelloBundleName {
  override def prepareBundleActor() = Props(HelloRoute("blended"))
}
