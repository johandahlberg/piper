import sbt._
import sbt.Keys._

object PiperBuild extends Build {

    lazy val piper = Project(
        id = "piper",
        base = file("."),
        settings = Project.defaultSettings ++
            Seq(
                scalacOptions in Compile ++= Seq("-deprecation", "–optimise", "-unchecked"),
                fork in Test := true,
                parallelExecution in Test := false)
                ++ dependencies)
        .configs(PipelineTestRun)
        .settings(inConfig(PipelineTestRun)(Defaults.testTasks) : _*)

    lazy val PipelineTestRun = config("pipelinetestrun").extend(Test)

    val dependencies = Seq(
        //libraryDependencies += "org.utgenome.thirdparty" % "picard" % "1.86.0",
        libraryDependencies += "commons-lang" % "commons-lang" % "2.5",
        libraryDependencies += "org.testng" % "testng" % "5.14.1",
        libraryDependencies += "log4j" % "log4j" % "1.2.16",
        libraryDependencies += "commons-io" % "commons-io" % "2.1",
        libraryDependencies += "net.java.dev.jets3t" % "jets3t" % "0.8.1",
        libraryDependencies += "org.simpleframework" % "simple-xml" % "2.0.4")
}
