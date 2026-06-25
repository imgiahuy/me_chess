package shared.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import upickle.default

/** Common HTTP utilities for microservices. */
object HttpHelpers {

  /** Creates a JSON HTTP entity from an object using upickle. */
  def jsonResponse[T: default.Writer](obj: T): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, default.write(obj))

  /** Parses a JSON string into an object using upickle. */
  def parseJson[T: default.Reader](body: String): Either[String, T] =
    try Right(default.read[T](body))
    catch { case e: Exception => Left(s"Invalid JSON request body: ${e.getMessage}") }
}
