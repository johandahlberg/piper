import sbt._
import sbt.Keys._

object PiperBuild extends Build {

  lazy val piper = Project(
    id = "piper",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "Piper",
      organization := "molmed",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.9.2",
      //libraryDependencies += "org.utgenome.thirdparty" % "picard" % "1.86.0",
      libraryDependencies += "commons-lang" % "commons-lang" % "2.5",
      libraryDependencies += "org.testng" % "testng" % "5.14.1"     
    )
    //++ seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)
    ++ seq(scalacOptions ++= Seq("-deprecation", "–optimise"))
  )
}
