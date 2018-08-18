package blended.security.login.impl

import akka.actor.{Actor, ActorSystem, Props}
import akka.util.Timeout
import blended.security.{BlendedPermissionManager, BlendedPermissions}
import blended.security.login.api.{AbstractTokenStore, Token, TokenHandler}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import akka.pattern.ask
import io.jsonwebtoken.Jwts
import prickle.{Pickle, Unpickle, Unpickler}
import blended.security.json.PrickleProtocol._

object TokenStoreMessages {

  case class GetToken(id: String)
  case class RemoveToken(id: String)
  case class StoreToken(t :Token)
  case object ListTokens
}

class MemoryTokenStore extends Actor {

  import TokenStoreMessages._

  private[this] val tokens : mutable.Map[String, Token] = mutable.Map.empty

  override def receive: Receive = {
    case GetToken(id) =>
      sender() ! tokens.get(id)

    case RemoveToken(id) =>
      sender() ! tokens.remove(id)

    case StoreToken(t : Token) =>
      val r = sender()
      if (tokens.get(t.id).isDefined) {
        r ! Failure(new Exception(s"token with id [${t.id}] already exists."))
      } else {
        tokens += (t.id -> t)
        r ! Success(t)
      }

    case ListTokens => sender() ! tokens.values.toSeq
  }
}

class SimpleTokenStore(
  mgr: BlendedPermissionManager,
  tokenHandler: TokenHandler,
  system: ActorSystem
) extends AbstractTokenStore(mgr, tokenHandler) {

  import TokenStoreMessages._

  private[this] implicit val timeout : Timeout = Timeout(1.second)
  private[this] implicit val eCtxt : ExecutionContext = system.dispatcher
  private[this] val storeActor = system.actorOf(Props[MemoryTokenStore])
  /**
    * @inheritdoc
    */
  override def getToken(user: String): Future[Option[Token]] =
    (storeActor ? GetToken(user)).mapTo[Option[Token]]

  /**
    * @inheritdoc
    */
  override def removeToken(user: String): Future[Option[Token]] =
    (storeActor ? RemoveToken(user)).mapTo[Option[Token]]

  /**
    * @inheritdoc
    */
  override def storeToken(token: Token): Future[Try[Token]] =
    (storeActor ? StoreToken(token)).mapTo[Try[Token]]

  override def listTokens(): Future[Seq[Token]] =
    (storeActor ? ListTokens).mapTo[Seq[Token]]

  override def verifyToken(token: String): Try[BlendedPermissions] =  {
    val claims = Jwts.parser().setSigningKey(publicKey()).parseClaimsJws(token)
    val permissions = claims.getBody().get("permissions", classOf[String])

    Unpickle[BlendedPermissions].fromString(permissions)
  }
}