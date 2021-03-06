package blended.security

import java.text.MessageFormat
import java.util

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

import blended.security.internal.{ LDAPLoginConfig, LdapSearchResult }
import blended.util.logging.Logger
import com.sun.jndi.ldap.LdapCtxFactory
import javax.naming.Context
import javax.naming.directory.{ DirContext, InitialDirContext, SearchControls }
import javax.security.auth.login.LoginException

class LDAPLoginModule extends AbstractLoginModule {

  private[this] val log = Logger[LDAPLoginModule]

  override protected val moduleName: String = "ldap"

  // convenience to extract the LDAP Config object
  lazy val ldapCfg : LDAPLoginConfig = LDAPLoginConfig.fromConfig(loginConfig)

  // obtain the initial LDAP Context
  private[this] lazy val dirContext : Try[DirContext] = Try {

    try {
      var env : mutable.Map[String, String] = mutable.Map(
        Context.INITIAL_CONTEXT_FACTORY -> classOf[LdapCtxFactory].getName(),
        Context.PROVIDER_URL -> ldapCfg.url,
        Context.SECURITY_AUTHENTICATION -> "simple"
      )

      env ++= ldapCfg.systemUser.map(u => (Context.SECURITY_PRINCIPAL -> u))
      env ++= ldapCfg.systemPassword.map(u => (Context.SECURITY_CREDENTIALS -> u))

      new InitialDirContext(new util.Hashtable[String, Object](env.asJava))
    } catch {
      case t : Throwable =>
        log.error(t)(t.getMessage())
        throw new LoginException(t.getMessage())
    }
  }

  @throws[LoginException]
  override def doLogin(): Boolean = {

    // First of all we will obtain the directory context

    try {
      dirContext.get
    } catch {
      case t : Throwable =>
        log.error(t)(t.getMessage())
        throw new LoginException(t.getMessage())
    }

    log.debug(s"Successfully connected to LDAP server [${ldapCfg.url}] user [${ldapCfg.systemUser}]")

    loggedInUser = Some(validateUser())
    true
  }

  override protected def postCommit(): Unit = dirContext.get.close()
  override protected def postAbort(): Unit = dirContext.get.close()
  override protected def postLogout(): Unit = dirContext.get.close()

  @throws[LoginException]
  private[this] def validateUser() : String = {

    try {
      val (user, pwd) = extractCredentials()

      val constraint = new SearchControls()
      constraint.setSearchScope(SearchControls.SUBTREE_SCOPE)

      val userSearchFormat = new MessageFormat(ldapCfg.userSearch)
      val filter = userSearchFormat.format(Array(doRFC2254Encoding(user)))

      val r = dirContext.get.search(ldapCfg.userBase, filter, constraint)

      LdapSearchResult(r).result match {
        case Nil => throw new LoginException(s"User [$user] not found in LDAP.")
        case head :: tail =>
          if (tail.length > 0) {
            log.warn(s"Search for user [$user] returned [${1 + tail.length}] records, using first record only.")
          }

          val name = head.getNameInNamespace()

          dirContext.get.addToEnvironment(Context.SECURITY_PRINCIPAL, name)
          dirContext.get.addToEnvironment(Context.SECURITY_CREDENTIALS, pwd)

          dirContext.get.getAttributes("", null)
          log.info(s"User [$user] authenticated with LDAP name [$name]")
          name
      }
    } catch {
      case t : Throwable =>
        log.error(t)(t.getMessage())
        throw new LoginException(t.getMessage())
    } finally {
      ldapCfg.systemUser match {
        case None => dirContext.get.removeFromEnvironment(Context.SECURITY_PRINCIPAL)
        case Some(u) => dirContext.get.addToEnvironment(Context.SECURITY_PRINCIPAL, u)
      }
      ldapCfg.systemPassword match {
        case None => dirContext.get.removeFromEnvironment(Context.SECURITY_CREDENTIALS)
        case Some(p) => dirContext.get.addToEnvironment(Context.SECURITY_CREDENTIALS, p)
      }
    }
  }

  @throws[LoginException]
  override def getGroups(member : String) : List[String] = {
    val constraint = new SearchControls()
    constraint.setSearchScope(SearchControls.SUBTREE_SCOPE)

    val groupSearchFormat = new MessageFormat(ldapCfg.groupSearch)
    val filter = groupSearchFormat.format(Array(doRFC2254Encoding(member)))

    val r = dirContext.get.search(ldapCfg.groupBase, filter, constraint)

    LdapSearchResult(r).result.map { sr =>
      sr.getAttributes().get(ldapCfg.groupAttribute).get().toString()
    }
  }

  // convenience method to encode a filter string
  private[this] def doRFC2254Encoding(inputString : String) : String = inputString match {
    case s if s.isEmpty() => ""
    case s if s.startsWith("\\") => "\\5c" + doRFC2254Encoding(s.substring(1))
    case s if s.startsWith("*") => "\\2a" + doRFC2254Encoding(s.substring(1))
    case s if s.startsWith("(") => "\\28" + doRFC2254Encoding(s.substring(1))
    case s if s.startsWith(")") => "\\29" + doRFC2254Encoding(s.substring(1))
    case s if s.startsWith("\0") => "\\00" + doRFC2254Encoding(s.substring(1))
    case s => s.substring(0,1) + doRFC2254Encoding(s.substring(1))
  }

}
