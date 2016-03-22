//import com.typesafe.sbt.SbtNativePackager._

// ********************************************
// The basics
// ********************************************

enablePlugins(JavaAppPackaging)
enablePlugins(RpmPlugin)

name in Global := "Piper"

organization := "molmed"

version in Global := "v1.3.0"

scalaVersion in Global := "2.10.1"

scalacOptions in Compile ++= Seq("-deprecation","-unchecked")

lazy val dependencies =
  Seq(
    "commons-lang" % "commons-lang" % "2.5",
    "org.testng" % "testng" % "5.14.1",
    "log4j" % "log4j" % "1.2.16",
    "commons-io" % "commons-io" % "2.1",
    "net.java.dev.jets3t" % "jets3t" % "0.8.1",
    "org.simpleframework" % "simple-xml" % "2.0.4",
    "com.github.scopt" %% "scopt" % "3.2.0")


lazy val commonSettings =  Defaults.coreDefaultSettings ++ Seq(
  libraryDependencies ++= dependencies
)


lazy val root = (project in file(".")).aggregate(piperCommon)


lazy val piperCommon = Project(
  id = "piper",
  base = file("piper-common"),
  settings = commonSettings ++ piperCommonSettings
)

lazy val piperCommonSettings = Seq(
  mainClass in Compile := Some("org.broadinstitute.gatk.queue.QCommandLine")
)

