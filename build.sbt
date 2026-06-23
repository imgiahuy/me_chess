ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.3"
ThisBuild / scalacOptions += "-language:postfixOps"
import sbt._
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

// --- Domain Layer (pure domain logic) ---
// DEPENDENCY RULE: Core must NOT depend on any other module (no dependencies)
// Contains: models, analysis, openings - pure domain logic only
lazy val core = project
  .in(file("core"))
  .settings(commonSettings,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "com.lihaoyi" %% "fastparse" % "3.1.1"
    )
  )

// --- Application Layer (services, controllers, use cases) ---
// DEPENDENCY RULE: Application depends on core (domain) and domainPersistence (interfaces)
// FORBIDDEN: Must NOT depend on infrastructurePersistence (concrete implementations)
// Contains: services, controllers, use cases, engines, formatters, parsers
lazy val application = project
  .in(file("application"))
  .settings(
    commonSettings,
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.14"
  )
  .dependsOn(core, domainPersistence, shared)

// --- Domain Persistence Layer (interfaces only) ---
// DEPENDENCY RULE: DomainPersistence depends only on core (domain) and shared
// FORBIDDEN: Must NOT depend on application or infrastructurePersistence
// Contains: repository and DAO interfaces (no implementations)
lazy val domainPersistence = project
  .in(file("domain-persistence"))
  .settings(commonSettings)
  .dependsOn(core, shared)

// --- Infrastructure Persistence Layer (implementations) ---
// DEPENDENCY RULE: InfrastructurePersistence depends on core, domainPersistence (interfaces), and shared
// FORBIDDEN: Must NOT depend on application (no business logic)
// Contains: concrete implementations (InMemoryGameRepository, DatabaseGameRepository, DAOs, database configs)
lazy val infrastructurePersistence = project
  .in(file("infrastructure-persistence"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % "3.5.1",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
      "com.h2database" % "h2" % "2.2.224",
      "org.postgresql" % "postgresql" % "42.7.4",
      "org.mongodb" % "mongodb-driver-sync" % "4.11.0",
      "redis.clients" % "jedis" % "5.0.0",
      "org.flywaydb" % "flyway-core" % "9.22.3",
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "com.lihaoyi" %% "upickle" % "3.1.0"
    )
  )
  .dependsOn(core, domainPersistence, shared)

lazy val shared = project
  .in(file("shared"))
  .settings(commonSettings)

// --- Presentation Layer: TUI ---
// DEPENDENCY RULE: TUI depends on core (domain), application (use cases), and shared
// FORBIDDEN: Must NOT depend on infrastructurePersistence directly
// Contains: terminal user interface
lazy val tui = project
  .in(file("tui"))
  .settings(
    commonSettings,
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-tui.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("tui.TuiEntry")
  )
  .dependsOn(core, application, shared)

// --- Presentation Layer: GUI ---
// DEPENDENCY RULE: GUI depends on core (domain), application (use cases), and shared
// FORBIDDEN: Must NOT depend on infrastructurePersistence directly
// Contains: ScalaFX graphical user interface
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
  .dependsOn(core, application, shared)

// --- Legacy Persistent Module (deprecated, use infrastructure-persistence) ---
// DEPENDENCY RULE: Legacy module - being replaced by infrastructure-persistence
// TODO: Migrate all code to infrastructure-persistence and remove this module
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
      "com.lihaoyi" %% "upickle" % "3.1.0",
      // Flyway for database migrations
      "org.flywaydb" % "flyway-core" % "9.22.3",
      // Redis client for caching
      "redis.clients" % "jedis" % "5.0.0",
      // Testcontainers for integration testing
      "org.testcontainers" % "testcontainers" % "1.19.8" % Test,
      "org.testcontainers" % "postgresql" % "1.19.8" % Test,
      "org.testcontainers" % "mongodb" % "1.19.8" % Test,
      "org.testcontainers" % "junit-jupiter" % "1.19.8" % Test,
      "org.slf4j" % "slf4j-simple" % "2.0.9" % Test
    )
  )
  .dependsOn(core, application, shared)

// --- Analytics Layer: Spark (analytics) ---
// DEPENDENCY RULE: Spark depends on core (domain) and shared
// Contains: Apache Spark analytics for game data analysis
lazy val spark = project
  .in(file("spark"))
  .settings(
    commonSettings,
    // JVM options required for Spark with Java 17+
    fork := true,
    run / javaOptions ++= Seq(
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.util=ALL-UNNAMED",
      "--add-opens=java.base/java.nio=ALL-UNNAMED",
      "--add-opens=java.base/java.net=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
      "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
      "--add-opens=java.base/sun.security.x509=ALL-UNNAMED",
      "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
    ),
    Test / javaOptions ++= Seq(
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.util=ALL-UNNAMED"
    ),
    libraryDependencies ++= Seq(
      // Apache Spark 3.5.1 (latest stable, uses Scala 2.13)
      // Spark doesn't have native Scala 3 support, so we use _2.13 artifacts
      // Exclude scala-xml to avoid conflicts with Scala 3's version
      ("org.apache.spark" % "spark-core_2.13" % "3.5.1")
        .exclude("org.scala-lang.modules", "scala-xml_2.13"),
      ("org.apache.spark" % "spark-sql_2.13" % "3.5.1")
        .exclude("org.scala-lang.modules", "scala-xml_2.13"),

      // Kafka integration for Spark
      ("org.apache.spark" % "spark-sql-kafka-0-10_2.13" % "3.5.1")
        .exclude("org.scala-lang.modules", "scala-xml_2.13"),
      "org.apache.kafka" % "kafka-clients" % "3.5.1",

      // MongoDB driver for reading real game data
      "org.mongodb" % "mongodb-driver-sync" % "4.11.1",

      // JSON processing
      "com.lihaoyi" %% "upickle" % "3.1.0",

      // Testing - include Spark in test scope (not provided)
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      ("org.apache.spark" % "spark-core_2.13" % "3.5.1" % Test)
        .exclude("org.scala-lang.modules", "scala-xml_2.13"),
      ("org.apache.spark" % "spark-sql_2.13" % "3.5.1" % Test)
        .exclude("org.scala-lang.modules", "scala-xml_2.13")
    ),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-spark.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("ChessSparkApp"),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", xs @ _*) => MergeStrategy.filterDistinctLines
      case PathList("META-INF", "MANIFEST.MF")       => MergeStrategy.discard
      case PathList("META-INF", xs @ _*)              => MergeStrategy.discard
      case "reference.conf"                           => MergeStrategy.concat
      case x                                          => MergeStrategy.first
    }
  )
  .dependsOn(core, shared)

// --- Presentation Layer: REST API ---
// DEPENDENCY RULE: REST API depends on core (domain), application (use cases), domainPersistence (interfaces), and shared
// FORBIDDEN: Must NOT depend on infrastructurePersistence directly (use dependency injection for implementations)
// Contains: Http4s REST API endpoints and controllers
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
      // Use 2.13 versions with scala-java8-compat exclusion for Scala 3 compatibility
      ("com.typesafe.akka" % "akka-actor-typed_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-http_3" % "10.5.3")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-stream_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-http-testkit_3" % "10.5.3" % Test)
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.14.0" % Test,
      "io.gatling"            % "gatling-test-framework"    % "3.14.0" % Test,
      // Kafka and Alpakka Kafka - use 2.13 version with all Akka exclusions
      ("com.typesafe.akka" % "akka-stream-kafka_2.13" % "4.0.2")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13")
        .exclude("com.typesafe.akka", "akka-actor_2.13")
        .exclude("com.typesafe.akka", "akka-stream_2.13")
        .exclude("com.typesafe.akka", "akka-protobuf-v3_2.13")
        .exclude("com.typesafe", "ssl-config-core_2.13"),
      "org.apache.kafka" % "kafka-clients" % "3.5.1",
      ("io.github.embeddedkafka" % "embedded-kafka_2.13" % "3.5.1" % Test)
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13")
        .exclude("com.typesafe.akka", "akka-actor_2.13")
        .exclude("com.typesafe.akka", "akka-stream_2.13")
        .exclude("com.typesafe.akka", "akka-protobuf-v3_2.13")
        .exclude("com.typesafe", "ssl-config-core_2.13"),
      // Scala 3 compatible scala-java8-compat
      "org.scala-lang.modules" % "scala-java8-compat_3" % "1.0.2"
    ),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-rest-api.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("api.ChessRestServer"),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyMergeStrategy := {
      case "reference.conf" => MergeStrategy.concat
      case "META-INF/MANIFEST.MF" => MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  )
  .dependsOn(core, application, domainPersistence, infrastructurePersistence, shared)

// --- Infrastructure Layer: Player Service (standalone microservice) ---
// DEPENDENCY RULE: Player service is a standalone microservice
// Contains: Akka HTTP-based player management service
lazy val playerService = project
  .in(file("player-service"))
  .settings(
    commonSettings,
    fork := true,
    libraryDependencies ++= Seq(
      ("com.typesafe.akka" % "akka-actor-typed_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-http_3" % "10.5.3")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-stream_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "org.scala-lang.modules" % "scala-java8-compat_3" % "1.0.2",
      "org.mongodb" % "mongodb-driver-sync" % "4.11.0"
    ),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-player-service.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("player.PlayerServiceMain"),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )

// --- Infrastructure Layer: Bot Service (Lichess Bot API integration) ---
// DEPENDENCY RULE: Bot service depends on core (domain), application (use cases), and shared
// Contains: Akka HTTP-based bot service for Lichess integration
lazy val botService = project
  .in(file("bot-service"))
  .settings(
    commonSettings,
    fork := true,
    libraryDependencies ++= Seq(
      ("com.typesafe.akka" % "akka-actor-typed_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-http_3" % "10.5.3")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-stream_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-http-testkit_3" % "10.5.3" % Test)
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "org.scala-lang.modules" % "scala-java8-compat_3" % "1.0.2"
    ),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-bot-service.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("lichess.LichessBotMain"),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )
  .dependsOn(core, application, shared)

// --- Infrastructure Layer: Tournament Service (bot evaluation arena + tournaments) ---
// DEPENDENCY RULE: Tournament service depends on core (domain), application (use cases), and shared
// Contains: Akka HTTP-based tournament management service
lazy val tournamentService = project
  .in(file("tournament-service"))
  .settings(
    commonSettings,
    fork := true,
    libraryDependencies ++= Seq(
      ("com.typesafe.akka" % "akka-actor-typed_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-http_3" % "10.5.3")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-stream_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-http-testkit_3" % "10.5.3" % Test)
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-stream-kafka_2.13" % "4.0.2")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13")
        .exclude("com.typesafe.akka", "akka-actor_2.13")
        .exclude("com.typesafe.akka", "akka-stream_2.13")
        .exclude("com.typesafe.akka", "akka-protobuf-v3_2.13")
        .exclude("com.typesafe", "ssl-config-core_2.13"),
      "org.apache.kafka" % "kafka-clients" % "3.5.1",
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "org.scala-lang.modules" % "scala-java8-compat_3" % "1.0.2",
      "org.mongodb" % "mongodb-driver-sync" % "4.11.0"
    ),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-tournament-service.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("tournament.TournamentServiceMain"),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )
  .dependsOn(core, application, shared)

// --- Tournament Client Layer (client for external Tournament Server) ---
// DEPENDENCY RULE: Tournament client depends on core (domain), application (use cases), and shared
// Contains: Client for integrating with external NowChess Tournament Server
lazy val tournamentClient = project
  .in(file("tournament-client"))
  .settings(
    commonSettings,
    fork := true,
    libraryDependencies ++= Seq(
      ("com.typesafe.akka" % "akka-actor-typed_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-http_3" % "10.5.3")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      ("com.typesafe.akka" % "akka-stream_3" % "2.8.5")
        .exclude("org.scala-lang.modules", "scala-java8-compat_2.13"),
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "org.scala-lang.modules" % "scala-java8-compat_3" % "1.0.2"
    ),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyJarName := "chess-tournament-client.jar",
    sbtassembly.AssemblyPlugin.autoImport.assembly / mainClass := Some("tournament.client.TournamentClientMain"),
    sbtassembly.AssemblyPlugin.autoImport.assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )
  .dependsOn(core, application, shared)

// --- Benchmark Layer: JMH Benchmarks ---
// DEPENDENCY RULE: Benchmark depends on core (domain) and application (services)
// Contains: JMH microbenchmarks for performance testing
lazy val benchmark = project
  .in(file("benchmark"))
  .enablePlugins(JmhPlugin)
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.openjdk.jmh" % "jmh-core" % "1.37" % "provided",
      "org.openjdk.jmh" % "jmh-generator-bytecode" % "1.37" % "provided"
    )
  )
  .dependsOn(core, application)

// --- Root project (aggregator only) ---
lazy val root = project
  .in(file("."))
  .aggregate(
    core,
    application,
    domainPersistence,
    infrastructurePersistence,
    shared,
    tui,
    gui,
    persistent,
    restApi,
    spark,
    playerService,
    botService,
    tournamentService,
    tournamentClient,
    benchmark
  )
  .settings(
    name := "me_chess"
  )