ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.7"

lazy val root = (project in file("."))
  .enablePlugins(ScoverageSbtPlugin)
  .settings(
    name := "me_chess",

    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,

    coverageExcludedPackages := ".*Main.*"

)
