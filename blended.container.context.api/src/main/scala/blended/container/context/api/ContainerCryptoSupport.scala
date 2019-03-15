package blended.container.context.api

import scala.util.Try

trait ContainerCryptoSupport {

  def encrypt(plain: String) : Try[String]
  def decrypt(encrypted : String) : Try[String]
}
