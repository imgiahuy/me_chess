package lichess

import upickle.default.{ReadWriter, macroRW, readwriter}
import ujson._

object LichessJsonCodecs {
  given ReadWriter[TimeControl] = macroRW
  given ReadWriter[Variant] = macroRW
  given ReadWriter[PlayerRef] = macroRW
  given ReadWriter[Perf] = macroRW
  given ReadWriter[Compat] = macroRW

  // Custom lenient codec for Challenge that ignores unknown fields
  given ReadWriter[Challenge] = readwriter[ujson.Value].bimap(
    challenge => ujson.Obj(
      "id" -> challenge.id,
      "status" -> challenge.status,
      "rated" -> challenge.rated,
      "speed" -> challenge.speed,
      "timeControl" -> challenge.timeControl.map(tc => upickle.default.writeJs(tc)).getOrElse(ujson.Null),
      "variant" -> upickle.default.writeJs(challenge.variant),
      "challenger" -> upickle.default.writeJs(challenge.challenger),
      "destUser" -> challenge.destUser.map(u => upickle.default.writeJs(u)).getOrElse(ujson.Null),
      "url" -> challenge.url.map(ujson.Str(_)).getOrElse(ujson.Null),
      "color" -> challenge.color.map(ujson.Str(_)).getOrElse(ujson.Null),
      "finalColor" -> challenge.finalColor.map(ujson.Str(_)).getOrElse(ujson.Null),
      "perf" -> challenge.perf.map(p => upickle.default.writeJs(p)).getOrElse(ujson.Null),
      "compat" -> challenge.compat.map(c => upickle.default.writeJs(c)).getOrElse(ujson.Null)
    ),
    json => {
      val obj = json.obj
      Challenge(
        id = obj.get("id").map(_.str).getOrElse(""),
        status = obj.get("status").map(_.str).getOrElse(""),
        rated = obj.get("rated").map(_.bool).getOrElse(false),
        speed = obj.get("speed").map(_.str).getOrElse(""),
        timeControl = obj.get("timeControl").flatMap(v => try Some(upickle.default.read[TimeControl](v)) catch { case _: Exception => None }),
        variant = obj.get("variant").flatMap(v => try Some(upickle.default.read[Variant](v)) catch { case _: Exception => None }).getOrElse(Variant("standard", "Standard")),
        challenger = obj.get("challenger").flatMap(v => try Some(upickle.default.read[PlayerRef](v)) catch { case _: Exception => None }).getOrElse(PlayerRef()),
        destUser = obj.get("destUser").flatMap(v => try Some(upickle.default.read[PlayerRef](v)) catch { case _: Exception => None }),
        url = obj.get("url").map(_.str),
        color = obj.get("color").map(_.str),
        finalColor = obj.get("finalColor").map(_.str),
        perf = obj.get("perf").flatMap(v => try Some(upickle.default.read[Perf](v)) catch { case _: Exception => None }),
        compat = obj.get("compat").flatMap(v => try Some(upickle.default.read[Compat](v)) catch { case _: Exception => None })
      )
    }
  )

  // Custom lenient codec for GameInfo that ignores unknown fields
  given ReadWriter[GameInfo] = readwriter[ujson.Value].bimap(
    gameInfo => ujson.Obj(
      "id" -> gameInfo.id,
      "color" -> gameInfo.color.map(ujson.Str(_)).getOrElse(ujson.Null),
      "speed" -> gameInfo.speed.map(ujson.Str(_)).getOrElse(ujson.Null),
      "rated" -> gameInfo.rated.map(ujson.Bool(_)).getOrElse(ujson.Null),
      "fullId" -> gameInfo.fullId.map(ujson.Str(_)).getOrElse(ujson.Null),
      "fen" -> gameInfo.fen.map(ujson.Str(_)).getOrElse(ujson.Null),
      "lastMove" -> gameInfo.lastMove.map(ujson.Str(_)).getOrElse(ujson.Null),
      "source" -> gameInfo.source.map(ujson.Str(_)).getOrElse(ujson.Null),
      "hasMoved" -> gameInfo.hasMoved.map(ujson.Bool(_)).getOrElse(ujson.Null),
      "isMyTurn" -> gameInfo.isMyTurn.map(ujson.Bool(_)).getOrElse(ujson.Null),
      "rating" -> gameInfo.rating.map(ujson.Num(_)).getOrElse(ujson.Null),
      "opponent" -> gameInfo.opponent.map(op => ujson.Obj(
        "id" -> op.id.map(ujson.Str(_)).getOrElse(ujson.Null),
        "name" -> op.name.map(ujson.Str(_)).getOrElse(ujson.Null),
        "rating" -> op.rating.map(ujson.Num(_)).getOrElse(ujson.Null)
      )).getOrElse(ujson.Null)
    ),
    json => {
      val obj = json.obj
      val opponent = obj.get("opponent").map(_.obj).map { oppObj =>
        PlayerRef(
          id = oppObj.get("id").map(_.str),
          name = oppObj.get("name").map(_.str).orElse(oppObj.get("username").map(_.str)),
          rating = oppObj.get("rating").map(_.num).map(_.toInt)
        )
      }
      GameInfo(
        id = obj.get("id").map(_.str).getOrElse(""),
        color = obj.get("color").map(_.str),
        speed = obj.get("speed").map(_.str),
        rated = obj.get("rated").map(_.bool),
        fullId = obj.get("fullId").map(_.str),
        fen = obj.get("fen").map(_.str),
        lastMove = obj.get("lastMove").map(_.str),
        source = obj.get("source").map(_.str),
        hasMoved = obj.get("hasMoved").map(_.bool),
        isMyTurn = obj.get("isMyTurn").map(_.bool),
        rating = obj.get("rating").map(_.num).map(_.toInt),
        opponent = opponent
      )
    }
  )
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
          // Determine color by checking if bot ID is in white or black object
          val botId = "nguyenphatgiahuy1101" // TODO: Get from config
          val color = obj.get("white").flatMap(_.obj.get("id").map(_.str)) match {
            case Some(id) if id == botId => "white"
            case _ => "black"
          }
          val opponent = obj.get("white").flatMap(_.obj.get("id").map(_.str)) match {
            case Some(id) if id == botId => obj.get("black").flatMap(v => try Some(upickle.default.read[PlayerRef](v)) catch { case _: Exception => None })
            case _ => obj.get("white").flatMap(v => try Some(upickle.default.read[PlayerRef](v)) catch { case _: Exception => None })
          }
          // Extract state from nested object
          val state = obj.get("state").flatMap { stateObj =>
            try {
              val moves = stateObj.obj.get("moves").map(_.str).getOrElse("")
              val wtime = stateObj.obj.get("wtime").map(_.num).map(_.toInt)
              val btime = stateObj.obj.get("btime").map(_.num).map(_.toInt)
              val winc = stateObj.obj.get("winc").map(_.num).map(_.toInt)
              val binc = stateObj.obj.get("binc").map(_.num).map(_.toInt)
              val status = stateObj.obj.get("status").map(_.str)
              Some(GameState(moves, wtime, btime, winc, binc, status, None))
            } catch { case _: Exception => None }
          }.getOrElse(GameState("", None, None, None, None, None, None))
          GameFull(id, initialFen, color, opponent, state)

        case Some("gameState") =>
          // Handle flat gameState structure
          try {
            val moves = obj.get("moves").map(_.str).getOrElse("")
            val wtime = obj.get("wtime").map(_.num).map(_.toInt)
            val btime = obj.get("btime").map(_.num).map(_.toInt)
            val winc = obj.get("winc").map(_.num).map(_.toInt)
            val binc = obj.get("binc").map(_.num).map(_.toInt)
            val status = obj.get("status").map(_.str)
            val winner = obj.get("winner").map(_.str)
            val fen = obj.get("fen").map(_.str)
            GameState(moves, wtime, btime, winc, binc, status, winner, None, fen)
          } catch { case _: Exception => UnknownEvent(ujson.write(json)) }

        case Some("unknown") | _ =>
          UnknownEvent(ujson.write(json))
      }
    }
  )
}