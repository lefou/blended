/*
 * Copyright 2014, WoQ - Way of Quality UG(mbH)
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.osgi.akka.system

import org.osgi.framework.{BundleActivator, BundleContext}
import akka.actor.{Props, ActorRef, ActorSystem}
import de.woq.osgi.akka.modules.RichBundleContext

case class InitializeBundle(context: BundleContext)

trait BundleName {
  def bundleSymbolicName : String
}

trait ActorSystemAware extends BundleActivator { this : BundleName =>

  var bundleContextRef : RichBundleContext = _
  var actorRef         : ActorRef = _

  def bundleContext : RichBundleContext = bundleContextRef
  def bundleActor : ActorRef = actorRef

  def prepareBundleActor() : Props

  final def start(context: BundleContext) {
    this.bundleContextRef = context

    bundleContext.findService(classOf[ActorSystem]).andApply { actorSystem =>
      actorRef = actorSystem.actorOf(prepareBundleActor(), bundleSymbolicName)
      actorRef ! InitializeBundle(context)
      postStartActor()
    }
  }

  def postStartActor() {}

  final def stop(context: BundleContext) {

    bundleContext.findService(classOf[ActorSystem]).andApply { actorSystem =>
      preStopActor()
      actorSystem.stop(bundleActor)
    }
  }

  def preStopActor() {}

}
