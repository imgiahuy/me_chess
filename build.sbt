ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.14"

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
  .settings(commonSettings)

lazy val tui = project
  .in(file("tui"))
  .settings(commonSettings)

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

      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "de.heikoseeberger" %% "akka-http-play-json" % "1.39.2"
    )
  )

// --- REST API (Http4s) ---
lazy val restApi = project
  .in(file("rest-api"))
  .dependsOn(core)
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "de.heikoseeberger" %% "akka-http-play-json" % "1.39.2"
    )
  )

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