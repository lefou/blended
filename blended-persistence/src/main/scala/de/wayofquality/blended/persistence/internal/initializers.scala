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

package de.wayofquality.blended.persistence.internal

import de.wayofquality.blended.akka.{ActorSystemAware, BundleName}
import akka.actor.Props

trait PersistenceBundleName extends BundleName {
  override def bundleSymbolicName = "de.wayofquality.blended.persistence"
}

class PersistenceActivator extends ActorSystemAware with PersistenceBundleName {
  override def prepareBundleActor() = Props(PersistenceManager(new Neo4jBackend()))
}