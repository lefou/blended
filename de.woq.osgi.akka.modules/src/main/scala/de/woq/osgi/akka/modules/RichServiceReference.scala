/*
 * Copyright 2009-2011 Weigle Wilczek GmbH
 * Modifications 2014- Way of Quality UG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.woq.osgi.akka.modules

import org.osgi.framework.ServiceReference

private[modules] class RichServiceReference[I](serviceReference: ServiceReference[I]) {

  assert(serviceReference != null, "The ServiceReference must not be null!")

  /**
   * Gives access to service properties as Props (alias for Scala Map[String, Any]).
   * @return The service properties
   */
  lazy val properties: Props = Map(propsFrom(serviceReference) : _*)

  private def propsFrom(serviceReference: ServiceReference[I]): Array[(String, AnyRef)] = {
    serviceReference.getPropertyKeys match {
      case null => Array[(String, AnyRef)]()
      case keys => keys map { key => (key, serviceReference.getProperty(key)) }
    }
  }
}
