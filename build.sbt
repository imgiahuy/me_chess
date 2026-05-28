ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.3"
ThisBuild / scalacOptions += "-language:postfixOps"
import sbtassembly.AssemblyPlugin.autoImport._

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
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-tui.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("tui.TuiEntry")
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
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-gui.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("gui.GuiEntry"),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x => MergeStrategy.first
    }
  )
  .dependsOn(core, shared)

lazy val persistent = project
  .in(file("persistent"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % "3.5.1",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
      "com.h2database" % "h2" % "2.2.224",
      "org.postgresql" % "postgresql" % "42.7.4",
      "org.mongodb" % "mongodb-driver-sync" % "4.11.0",
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "com.lihaoyi" %% "upickle" % "3.1.0"
    )
  )
  .dependsOn(core, shared)

// --- REST API (Http4s) ---
lazy val restApi = project
  .in(file("rest-api"))
  .enablePlugins(GatlingPlugin)
  .settings(
    commonSettings,
    fork := true,
    Test / javaOptions ++= Seq(
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.util=ALL-UNNAMED"
    ),
    Gatling / javaOptions ++= Seq(
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.util=ALL-UNNAMED"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "com.typesafe.akka" %% "akka-http-testkit" % "10.5.3" % Test,
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.14.0" % Test,
      "io.gatling"            % "gatling-test-framework"    % "3.14.0" % Test
    ),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-rest-api.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("api.ChessRestServer"),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
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