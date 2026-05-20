ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.3"

// --- OS detection for JavaFX ---
val osName = System.getProperty("os.name").toLowerCase
val platform =
  if (osName.contains("win")) "win"
  else if (osName.contains("mac")) "mac"
  else "linux"

// --- Common settings ---
lazy val commonSettings = Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

// --- Modules ---

lazy val core = project
  .in(file("core"))
  .settings(commonSettings,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "com.lihaoyi" %% "fastparse" % "3.1.1"
    )
  )

lazy val shared = project
  .in(file("shared"))
  .settings(commonSettings)

lazy val tui = project
  .in(file("tui"))
  .settings(
    commonSettings,
    assembly / assemblyJarName := "chess-tui.jar",
    assembly / mainClass := Some("tui.TuiEntry")
  )
  .dependsOn(core, shared)

// --- GUI (ScalaFX) ---
lazy val gui = project
  .in(file("gui"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.scalafx" %% "scalafx" % "21.0.0-R32",

      // JavaFX
      "org.openjfx" % "javafx-base" % "21" classifier platform,
      "org.openjfx" % "javafx-controls" % "21" classifier platform,
      "org.openjfx" % "javafx-graphics" % "21" classifier platform,

    ),
    assembly / assemblyJarName := "chess-gui.jar",
    assembly / mainClass := Some("gui.GuiEntry"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x => MergeStrategy.first
    }
  )
  .dependsOn(core, shared)

lazy val persistent = project
  .in(file("persistent"))
  .settings(commonSettings)
  .dependsOn(core, shared)

// --- REST API (Http4s) ---
lazy val restApi = project
  .in(file("rest-api"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "com.lihaoyi" %% "upickle" % "3.1.0"
    ),
    assembly / assemblyJarName := "chess-rest-api.jar",
    assembly / mainClass := Some("api.ChessRestServer")
  )
  .dependsOn(core, persistent, shared)

// --- Root project (aggregator only) ---
lazy val root = project
  .in(file("."))
  .aggregate(
    tui,
    gui,
    restApi
  )
  .settings(
    name := "me_chess"
  )