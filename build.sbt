ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.14"

// Detect OS for JavaFX
val osName = System.getProperty("os.name").toLowerCase

val platform =
    if (osName.contains("win")) "win"
    else if (osName.contains("mac")) "mac"
    else "linux"

lazy val root = (project in file("."))
  .enablePlugins(ScoverageSbtPlugin)
  .settings(
      name := "me_chess",

      libraryDependencies ++= Seq(
          // Test
          "org.scalatest" %% "scalatest" % "3.2.19" % Test,

          // ScalaFX
          "org.scalafx" %% "scalafx" % "21.0.0-R32",

          // JavaFX (REQUIRED)
          "org.openjfx" % "javafx-base" % "21" classifier platform,
          "org.openjfx" % "javafx-controls" % "21" classifier platform,
          "org.openjfx" % "javafx-graphics" % "21" classifier platform,

          "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
          "com.typesafe.akka" %% "akka-http" % "10.5.3",
          "com.typesafe.akka" %% "akka-stream" % "2.8.5",
          "de.heikoseeberger" %% "akka-http-play-json" % "1.39.2"
      )
  )