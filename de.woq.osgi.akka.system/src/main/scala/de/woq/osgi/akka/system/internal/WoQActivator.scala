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

package de.woq.osgi.akka.system.internal

import akka.osgi.ActorSystemActivator
import org.osgi.framework.BundleContext
import akka.actor.{Props, ActorSystem}
import akka.event.{LogSource, Logging}
import de.woq.osgi.akka.system.WOQAkkaConstants._
import de.woq.osgi.java.container.context.ContainerContext
import com.typesafe.config.{ConfigFactory, Config}
import java.io.File
import de.woq.osgi.akka.system.osgi.internal.OSGIFacade
import de.woq.osgi.akka.modules._

class WoQActivator extends ActorSystemActivator {

  def configure(osgiContext: BundleContext, system: ActorSystem) {
    val log = Logging(system, this)

    log info "Creating Config Locator actor"
    system.actorOf(Props(ConfigLocator(configDir(osgiContext))), configLocatorPath)

    log info "Creating Akka OSGi Facade"
    system.actorOf(Props[OSGIFacade], osgiFacadePath)

    log info "Registering Actor System as Service."
    registerService(osgiContext, system)

    log.info("ActorSystem [" + system.name + "] initialized." )

  }

  override def getActorSystemName(context: BundleContext): String = "WoQActorSystem"

  override def getActorSystemConfiguration(context: BundleContext): Config = {
    ConfigFactory.parseFile(new File(configDir(context), "application.conf"))
  }
  
  private[WoQActivator] def configDir(implicit osgiContext : BundleContext) = {

    val defaultConfigDir = System.getProperty("karaf.home") + "/etc"

    (osgiContext findService(classOf[ContainerContext])) match {
      case Some(svcRef) => svcRef invokeService { ctx => ctx.getContainerConfigDirectory } match {
        case Some(s)  => s
        case _ => defaultConfigDir
      }
      case _ => defaultConfigDir
    }
  }

}

object WoQActivator {
  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }
}