package blended.security.internal

import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag
import javax.security.auth.login.{AppConfigurationEntry, Configuration}

import blended.security.ShiroLoginModule
import blended.security.boot.BlendedLoginModule
import BlendedLoginModule.{propBundle, propModule}
import org.osgi.framework.BundleContext

import scala.collection.JavaConverters._

class BlendedConfiguration(bundleContext: BundleContext) extends Configuration {

  override def getAppConfigurationEntry(name: String): Array[AppConfigurationEntry] = {

    val options : Map[String, Any] = Map(
      propBundle -> bundleContext.getBundle().getSymbolicName(),
      propModule -> classOf[ShiroLoginModule].getName()
    )

    val entry = new AppConfigurationEntry(classOf[BlendedLoginModule].getName(), LoginModuleControlFlag.REQUISITE, options.asJava)

    Array(entry)
  }
}