package gui

import controller.{GameController, GameControllerInterface}
import scalafx.scene.layout.BorderPane
import scalafx.scene.control.{Alert, Button, ButtonType, Dialog, TextField}
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.Scene
import scalafx.stage.Stage
import scalafx.Includes.*
import scalafx.animation.AnimationTimer
import model.PositionState

class GuiController(gameControllerInterface: GameControllerInterface) {

  private var state: PositionState = null
  private var selected: Option[(Int, Int)] = None
  private var isProcessingMove: Boolean = false
  private var validMoves: Set[(Int, Int)] = Set()
  private var showingSetup: Boolean = true
  private var moveHistory: List[String] = List()
  private var notification: String = "Welcome to Chess!"
  private var turnStartTime: Long = System.currentTimeMillis()
  private var clockTimer: AnimationTimer = null

  val root = new BorderPane()

  def start(): Unit = {
    showPlayerSetup()
  }

  private def showPlayerSetup(): Unit = {
    showingSetup = true
    root.center = GuiRenderer.renderPlayerSetup(
      (whiteName, blackName, timeControl) => handleStartGame(whiteName, blackName, timeControl),
      handleExit
    )
  }

  private def render(): Unit = {
    showingSetup = false

    // Calculate elapsed time for current player
    val elapsed = System.currentTimeMillis() - turnStartTime
    val (whiteRemaining, blackRemaining) = state.turn match {
      case model.White =>
        val whiteTime = state.whiteTime.map(t => Math.max(0, t.remainingTimeMs - elapsed))
        val blackTime = state.blackTime.map(_.remainingTimeMs)
        (whiteTime, blackTime)
      case model.Black =>
        val blackTime = state.blackTime.map(t => Math.max(0, t.remainingTimeMs - elapsed))
        val whiteTime = state.whiteTime.map(_.remainingTimeMs)
        (whiteTime, blackTime)
    }

    // Check if current player's time has run out
    if (state.gameResult == model.Ongoing) {
      val currentTimeOut = state.turn match {
        case model.White => whiteRemaining.exists(_ <= 0)
        case model.Black => blackRemaining.exists(_ <= 0)
      }
      if (currentTimeOut) {
        val winner = state.turn.opposite
        state = state.copy(gameResult = model.TimeOut(winner))
        notification = s"TIME OUT! $winner wins!"
      }
    }

    root.center = GuiRenderer.renderGameUI(
      state.board,
      handleClick,
      handleExit,
      handleSave,
      handleLoad,
      handleExportPgn,
      handleResign,
      handleReturnToMenu,
      selected,
      validMoves,
      moveHistory,
      notification,
      state.gameResult,
      whiteRemaining,
      blackRemaining
    )
  }

  private def handleClick(col: Int, row: Int): Unit = {
    // Prevent moves when game is over
    if (state.gameResult != model.Ongoing) {
      notification = "Game is over! No more moves allowed."
      render()
      return
    }

    // Prevent rapid clicks from being processed
    if (isProcessingMove) {
      return
    }

    selected match {

      case None =>
        // First click: select a piece
        if (state.board.pieceAt(model.Position(col, row)).isDefined) {
          selected = Some((col, row))
          // TODO: Calculate valid moves for the selected piece
          validMoves = Set()
          render()
        }

      case Some((fromCol, fromRow)) =>
        // Second click: attempt to move
        if ((col, row) == (fromCol, fromRow)) {
          // Deselect if clicking the same square
          selected = None
          validMoves = Set()
          render()
        } else {
          isProcessingMove = true
          val fromPos = model.Position(fromCol, fromRow)
          val toPos = model.Position(col, row)

          // Check if this is a pawn promotion
          val isPromotion = state.board.pieceAt(fromPos).exists { piece =>
            piece.pieceType == model.Pawn && (
              (piece.color == model.White && row == 7) ||
              (piece.color == model.Black && row == 0)
            )
          }

          if (isPromotion) {
            // Show promotion dialog
            showPromotionDialog(fromPos, toPos)
          } else {
            executeMove(fromPos, toPos, None)
          }
        }
    }
  }

  private def showPromotionDialog(fromPos: model.Position, toPos: model.Position): Unit = {
    import scalafx.scene.control.ChoiceDialog

    val choices = List(model.Queen, model.Rook, model.Bishop, model.Knight)
    val dialog = new ChoiceDialog[Object](model.Queen, choices) {
      title = "Pawn Promotion"
      headerText = "Choose a piece for promotion"
      contentText = "Select piece:"
    }

    val result = dialog.showAndWait()
    result match {
      case Some(pieceType: model.PieceType) =>
        executeMove(fromPos, toPos, Some(model.Promotion(pieceType)))
      case _ =>
        // User cancelled promotion
        isProcessingMove = false
        selected = None
        validMoves = Set()
        render()
    }
  }

  private def executeMove(fromPos: model.Position, toPos: model.Position, specialMove: Option[model.SpecialMoveType]): Unit = {
    val moveStr = s"${toAlg(fromPos.col, fromPos.row)}${toAlg(toPos.col, toPos.row)}${specialMove match {
      case Some(model.Promotion(pt)) => pt match {
        case model.Queen => "q"
        case model.Rook => "r"
        case model.Bishop => "b"
        case model.Knight => "n"
        case _ => ""
      }
      case _ => ""
    }}"

    val result = gameControllerInterface.makeMove(state, moveStr)

    result match {
      case Right(newState) =>
        // Update time in state
        val elapsed = System.currentTimeMillis() - turnStartTime
        val timeSpentMs = elapsed
        val increment = state.timeControl.map(_.incrementMs).getOrElse(0L)
        val updatedState = if (state.whiteTime.isDefined && state.blackTime.isDefined) {
          gameControllerInterface.asInstanceOf[GameController].updateTimeAfterMove(newState, timeSpentMs, increment)
        } else {
          newState
        }

        state = updatedState
        selected = None
        validMoves = Set()
        moveHistory = moveHistory :+ moveStr
        turnStartTime = System.currentTimeMillis()
        notification = s"Move: $moveStr"
      case Left(err) =>
        println(err)
        notification = s"Invalid move: $err"
        // Invalid move: keep selection and show error
    }

    isProcessingMove = false
    render()
  }

  private def handleExit(): Unit = {
    println("Goodbye!")
    sys.exit(0)
  }

  private def handleSave(): Unit = {
    import scalafx.stage.FileChooser
    import scalafx.stage.FileChooser.ExtensionFilter

    val fileChooser = new FileChooser() {
      title = "Save Game"
      initialFileName = "chess_game.json"
      extensionFilters.add(new ExtensionFilter("JSON Files", "*.json"))
    }

    val file = fileChooser.showSaveDialog(root.getScene.getWindow)
    if (file != null) {
      try {
        gameControllerInterface.save(state, file.toPath)
        notification = "Game saved successfully!"
        render()
      } catch {
        case e: Exception =>
          notification = s"Error saving: ${e.getMessage}"
          render()
      }
    }
  }

  private def handleLoad(): Unit = {
    import scalafx.stage.FileChooser
    import scalafx.stage.FileChooser.ExtensionFilter

    val fileChooser = new FileChooser() {
      title = "Load Game"
      extensionFilters.add(new ExtensionFilter("JSON Files", "*.json"))
    }

    val file = fileChooser.showOpenDialog(root.getScene.getWindow)
    if (file != null) {
      try {
        state = gameControllerInterface.load(file.toPath)
        selected = None
        validMoves = Set()
        moveHistory = state.moveHistory.map(move => s"${toAlg(move.from.col, move.from.row)}${toAlg(move.to.col, move.to.row)}")
        notification = "Game loaded successfully!"
        render()
      } catch {
        case e: Exception =>
          notification = s"Error loading: ${e.getMessage}"
          render()
      }
    }
  }

  private def toAlg(col: Int, row: Int): String = {
    val file = ('a' + col).toChar
    val rank = (row + 1).toString
    s"$file$rank"
  }

  private def handleStartGame(whiteName: String, blackName: String, timeControl: Option[model.TimeControl]): Unit = {
    val whitePlayer = if (whiteName.trim.nonEmpty) whiteName.trim else "White"
    val blackPlayer = if (blackName.trim.nonEmpty) blackName.trim else "Black"

    state = timeControl match {
      case Some(tc) => gameControllerInterface.asInstanceOf[GameController].createWithTimeControl(whitePlayer, blackPlayer, tc)
      case None => gameControllerInterface.create(whitePlayer, blackPlayer)
    }
    moveHistory = List()
    notification = s"Game started: $whitePlayer vs $blackPlayer"
    turnStartTime = System.currentTimeMillis()
    startClockTimer()
    render()
  }

  private def startClockTimer(): Unit = {
    if (clockTimer != null) {
      clockTimer.stop()
    }
    clockTimer = AnimationTimer { now =>
      if (!showingSetup && state != null && state.gameResult == model.Ongoing) {
        // Update clock once per second (in nanoseconds: 1e9)
        if (now % 1_000_000_000L < 16_000_000L) {
          render()
        }
      }
    }
    clockTimer.start()
  }

  private def handleExportPgn(): Unit = {
    try {
      gameControllerInterface.exportToPgn(state, "GUI Game", "Local", "game.pgn")
      notification = "Game exported successfully to 'game.pgn'"
      render()
    } catch {
      case e: Exception =>
        notification = s"Failed to export game: ${e.getMessage}"
        render()
    }
  }

  private def handleResign(): Unit = {
    try {
      val color = if (state.turn == model.White) model.White else model.Black
      val newState = gameControllerInterface.asInstanceOf[GameController].resign(state, color)
      state = newState
      selected = None
      validMoves = Set()
      notification = s"${color.toString} resigned - ${color.opposite} wins!"
      render()
    } catch {
      case e: Exception =>
        notification = s"Failed to process resignation: ${e.getMessage}"
        render()
    }
  }

  private def handleReturnToMenu(): Unit = {
    showPlayerSetup()
  }
}
