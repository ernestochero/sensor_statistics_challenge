import sbt._

object Versions {
  val scala2                  = "2.13.8"
  val zio                     = "2.0.6"
  val zioNio                  = "2.0.0"
  val zioConfig               = "3.0.2"
  val zioLogging              = "2.1.0"
  val zioPrelude              = "1.0.0-RC14"
  val scalatest               = "3.2.15"
  val logbackClassic          = "1.2.11"
  val circe                   = "0.14.2"
  val scalafixOrganizeImports = "0.6.0"
}

object Dependencies {
  lazy val scalaTest  = "org.scalatest" %% "scalatest"    % Versions.scalatest
  lazy val zio        = "dev.zio"       %% "zio"          % Versions.zio
  lazy val zioStream  = "dev.zio"       %% "zio-streams"  % Versions.zio
  lazy val zioNioCore = "dev.zio"       %% "zio-nio-core" % Versions.zioNio
  lazy val zioNio     = "dev.zio"       %% "zio-nio"      % Versions.zioNio
  lazy val circe      = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % Versions.circe)

  lazy val zioTest    = Seq(
    "dev.zio" %% "zio-test"     % Versions.zio % "test",
    "dev.zio" %% "zio-test-sbt" % Versions.zio % "test"
  )
}
