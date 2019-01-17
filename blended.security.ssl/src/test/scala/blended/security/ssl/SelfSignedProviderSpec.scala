package blended.security.ssl

import java.security.{KeyPair, KeyPairGenerator, SecureRandom}
import java.security.cert.X509Certificate

import blended.testsupport.scalatest.LoggingFreeSpec
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.scalacheck.Gen
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.util.{Success, Try}

class SelfSignedProviderSpec extends LoggingFreeSpec
  with Matchers
  with PropertyChecks {

  private val keyStrength : Int = 2048
  private val sigAlg : String = "SHA256withRSA"
  private val validDays : Int = 365

  private val kpg : KeyPairGenerator = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(keyStrength, new SecureRandom())

    kpg
  }

  private class HostnameCNProvider(hostName: String) extends CommonNameProvider {
    override def commonName(): Try[String] = Success(s"CN=$hostName")
  }

  "The self signed certificate provider should" - {

    "create a self signed certificate with the hostname pupolated signed with it's own key" in new CertificateRequestBuilder with CertificateSigner {

      forAll(Gen.alphaNumStr){ n =>
        whenever(n.trim().nonEmpty) {
          val caKeys : KeyPair = kpg.generateKeyPair()

          val certReq : X509v3CertificateBuilder = hostCertificateRequest(
            cnProvider = new HostnameCNProvider(n),
            keyPair = caKeys
          ).get

          val cert : X509Certificate = sign(certReq, sigAlg, caKeys.getPrivate()).get
          val serverCert : ServerCertificate = ServerCertificate.create(caKeys, List(cert)).get

          cert.getIssuerDN().toString should be (cert.getSubjectX500Principal().toString)

          serverCert.chain should have size(1)
          assert(Try { serverCert.chain.head.verify(caKeys.getPublic()) } isSuccess)
        }
      }
    }
  }

}
