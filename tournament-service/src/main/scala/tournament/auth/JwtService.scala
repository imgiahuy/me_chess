package tournament.auth

import java.security.{KeyFactory, PrivateKey, PublicKey, Signature}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64
import java.time.Instant
import scala.util.{Try, Success, Failure}
import javax.crypto.Cipher
import java.security.spec.RSAPublicKeySpec
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.spec.RSAPrivateKeySpec
import java.math.BigInteger

/** JWT token claims */
case class JwtClaims(
    sub: String,           // Subject (bot ID)
    name: String,          // Bot name
    family: String,        // Bot family
    iat: Long,             // Issued at (timestamp)
    exp: Long,             // Expiration (timestamp)
    iss: String = "me-chess-tournament"  // Issuer
)

/** JWT authentication service */
class JwtService(privateKey: String, publicKey: String):
  
  private val algorithm = "RSA"
  private val signatureAlgorithm = "SHA256withRSA"
  private val tokenExpirationHours = 24
  
  /** Generate a JWT token for a bot */
  def generateToken(botId: String, botName: String, botFamily: String): Either[String, String] =
    Try {
      val now = Instant.now().getEpochSecond
      val exp = now + (tokenExpirationHours * 3600)
      
      val claims = JwtClaims(
        sub = botId,
        name = botName,
        family = botFamily,
        iat = now,
        exp = exp
      )
      
      val header = """{"alg":"RS256","typ":"JWT"}"""
      val payload = s"""{"sub":"${claims.sub}","name":"${claims.name}","family":"${claims.family}","iat":${claims.iat},"exp":${claims.exp},"iss":"${claims.iss}"}"""
      
      val encodedHeader = base64UrlEncode(header)
      val encodedPayload = base64UrlEncode(payload)
      val dataToSign = s"$encodedHeader.$encodedPayload"
      
      val signature = sign(dataToSign)
      val encodedSignature = base64UrlEncodeBytes(signature)
      
      s"$dataToSign.$encodedSignature"
    }.toEither match
      case Right(token) => Right(token)
      case Left(e) => Left(e.getMessage)
  
  /** Validate a JWT token and return claims if valid */
  def validateToken(token: String): Either[String, JwtClaims] =
    Try(token.split("\\.")) match
      case Success(parts) =>
        if parts.length != 3 then Left("Invalid token format")
        else
          val encodedHeader = parts(0)
          val encodedPayload = parts(1)
          val encodedSignature = parts(2)
          val dataToSign = s"$encodedHeader.$encodedPayload"
          
          Try(base64UrlDecode(encodedSignature)) match
            case Success(signature) =>
              if verify(dataToSign, signature) then
                Try(new String(java.util.Base64.getUrlDecoder.decode(encodedPayload))) match
                  case Success(payload) =>
                    Try(parseClaims(payload)) match
                      case Success(claims) =>
                        val now = Instant.now().getEpochSecond
                        if claims.exp >= now then Right(claims)
                        else Left("Token expired")
                      case Failure(e) => Left(e.getMessage)
                  case Failure(e) => Left(e.getMessage)
              else Left("Invalid signature")
            case Failure(e) => Left(e.getMessage)
      case Failure(e) => Left(e.getMessage)
  
  private def base64UrlEncode(data: String): String =
    java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(data.getBytes("UTF-8"))
  
  private def base64UrlEncodeBytes(data: Array[Byte]): String =
    java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(data)
  
  private def base64UrlDecode(data: String): Array[Byte] =
    java.util.Base64.getUrlDecoder.decode(data)
  
  private def sign(data: String): Array[Byte] =
    val key = parsePrivateKey(privateKey)
    val signature = Signature.getInstance(signatureAlgorithm)
    signature.initSign(key)
    signature.update(data.getBytes("UTF-8"))
    signature.sign()
  
  private def verify(data: String, signature: Array[Byte]): Boolean =
    Try {
      val key = parsePublicKey(publicKey)
      val sig = Signature.getInstance(signatureAlgorithm)
      sig.initVerify(key)
      sig.update(data.getBytes("UTF-8"))
      sig.verify(signature)
    }.getOrElse(false)
  
  private def parsePrivateKey(key: String): PrivateKey =
    val keyBytes = java.util.Base64.getDecoder.decode(key)
    val spec = new PKCS8EncodedKeySpec(keyBytes)
    KeyFactory.getInstance(algorithm).generatePrivate(spec)
  
  private def parsePublicKey(key: String): PublicKey =
    val keyBytes = java.util.Base64.getDecoder.decode(key)
    val spec = new X509EncodedKeySpec(keyBytes)
    KeyFactory.getInstance(algorithm).generatePublic(spec)
  
  private def parseClaims(json: String): JwtClaims =
    import upickle.default._
    read[JwtClaims](json)

object JwtService:
  /** Generate a new RSA key pair for testing/development */
  def generateKeyPair(): (String, String) =
    import java.security.{KeyPairGenerator, KeyPair}
    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048)
    val keyPair = keyGen.generateKeyPair()
    
    val privateKey = java.util.Base64.getEncoder.encodeToString(keyPair.getPrivate.getEncoded)
    val publicKey = java.util.Base64.getEncoder.encodeToString(keyPair.getPublic.getEncoded)
    
    (privateKey, publicKey)
  
  /** Create JWT service with default test keys */
  def create(): JwtService =
    val (privateKey, publicKey) = generateKeyPair()
    new JwtService(privateKey, publicKey)
  
  /** Create JWT service with provided keys */
  def createWithKeys(privateKey: String, publicKey: String): JwtService =
    new JwtService(privateKey, publicKey)

// JSON codecs for JwtClaims
given upickle.default.ReadWriter[JwtClaims] = upickle.default.macroRW
