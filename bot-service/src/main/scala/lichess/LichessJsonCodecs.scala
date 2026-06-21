package lichess

import upickle.default.{ReadWriter, macroRW, readwriter}
import ujson._

object LichessJsonCodecs {
  given ReadWriter[TimeControl] = macroRW
  given ReadWriter[Variant] = macroRW
  given ReadWriter[PlayerRef] = macroRW
  given ReadWriter[Challenge] = macroRW
  given ReadWriter[GameInfo] = macroRW
  given ReadWriter[GameState] = macroRW
  given ReadWriter[GameFull] = macroRW
  given ReadWriter[UnknownEvent] = macroRW
  given ReadWriter[LichessBotConfig] = macroRW
  given ReadWriter[ChallengeFilter] = macroRW

  given ReadWriter[LichessEvent] = readwriter[ujson.Value].bimap(
    _ => ujson.Obj(),
    json => {
      val obj = json.obj
      val t = obj.get("type").map(_.str).getOrElse("")
      val challenge = obj.get("challenge").flatMap(v =>
        try Some(upickle.default.read[Challenge](v))
        catch { case _: Exception => None }
      )
      val game = obj.get("game").flatMap(v =>
        try Some(upickle.default.read[GameInfo](v))
        catch { case _: Exception => None }
      )
      LichessEvent(t, challenge, game)
    }
  )

  given ReadWriter[GameStreamEvent] = readwriter[ujson.Value].bimap(
    _ => ujson.Obj(),
    json => {
      val obj = json.obj
      obj.get("type").map(_.str) match {
        case Some("gameFull") =>
          val id = obj.get("id").map(_.str).getOrElse("")
          val initialFen = obj.get("initialFen").map(_.str)
          val color = obj.get("color").map(_.str).orElse(obj.get("player").flatMap(_.obj.get("color")).map(_.str)).getOrElse("white")
          val opponent = obj.get("opponent").flatMap(v =>
            try Some(upickle.default.read[PlayerRef](v))
            catch { case _: Exception => None }
          )
          val state = obj.get("state").flatMap(v =>
            try Some(upickle.default.read[GameState](v))
            catch { case _: Exception => None }
          ).getOrElse(GameState("", None, None, None, None, None, None))
          GameFull(id, initialFen, color, opponent, state)

        case Some("gameState") =>
          upickle.default.read[GameState](json)

        case Some("unknown") | _ =>
          UnknownEvent(ujson.write(json))
      }
    }
  )
}