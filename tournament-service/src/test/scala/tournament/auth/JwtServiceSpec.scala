package tournament.auth

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JwtServiceSpec extends AnyFunSuite with Matchers {

  test("JwtService can generate a token") {
    val service = JwtService.create()
    val result = service.generateToken("bot-1", "Test Bot", "heuristic")
    
    result should matchPattern { case Right(_) => }
    val token = result.toOption.get
    token should not be empty
    token.contains(".") shouldBe true
  }

  test("JwtService generates token with three parts") {
    val service = JwtService.create()
    val result = service.generateToken("bot-1", "Test Bot", "heuristic")
    
    val token = result.toOption.get
    val parts = token.split("\\.")
    parts should have size 3
  }

  test("JwtService can validate a valid token") {
    val service = JwtService.create()
    val token = service.generateToken("bot-1", "Test Bot", "heuristic").toOption.get
    
    val result = service.validateToken(token)
    
    result should matchPattern { case Right(_) => }
    val claims = result.toOption.get
    claims.sub shouldEqual "bot-1"
    claims.name shouldEqual "Test Bot"
    claims.family shouldEqual "heuristic"
  }

  test("JwtService rejects invalid token format") {
    val service = JwtService.create()
    val result = service.validateToken("invalid.token")
    
    result should matchPattern { case Left(_) => }
  }

  test("JwtService rejects malformed token") {
    val service = JwtService.create()
    val result = service.validateToken("not.a.valid.token.format")
    
    result should matchPattern { case Left(_) => }
  }

  test("JwtService rejects token with invalid signature") {
    val service1 = JwtService.create()
    val service2 = JwtService.create()
    val token = service1.generateToken("bot-1", "Test Bot", "heuristic").toOption.get
    
    val result = service2.validateToken(token)
    
    result should matchPattern { case Left(_) => }
  }

  test("JwtService can create with custom keys") {
    val (privateKey, publicKey) = JwtService.generateKeyPair()
    val service = JwtService.createWithKeys(privateKey, publicKey)
    
    val token = service.generateToken("bot-1", "Test Bot", "heuristic").toOption.get
    val result = service.validateToken(token)
    
    result should matchPattern { case Right(_) => }
  }

  test("JwtService generates RSA key pair") {
    val (privateKey, publicKey) = JwtService.generateKeyPair()
    
    privateKey should not be empty
    publicKey should not be empty
    privateKey should not equal publicKey
  }

  test("JwtService token contains correct claims") {
    val service = JwtService.create()
    val token = service.generateToken("bot-123", "My Bot", "uci-engine").toOption.get
    
    val result = service.validateToken(token)
    val claims = result.toOption.get
    
    claims.sub shouldEqual "bot-123"
    claims.name shouldEqual "My Bot"
    claims.family shouldEqual "uci-engine"
    claims.iss shouldEqual "me-chess-tournament"
    claims.iat should be > 0L
    claims.exp should be > claims.iat
  }
}
