import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object PiperBuild extends Build {

  lazy val piper = Project(
    id = "piper",
    base = file("."),
    settings = Project.defaultSettings ++
      Seq(
        scalacOptions in Compile ++= Seq("-deprecation", "-unchecked"),
        fork in Test := true,
        mappings in (Compile, packageBin) ~=
          (_ filter {
            case (f, s) =>
              !s.contains("molmed/qscripts")}),
        parallelExecution in Test := false) ++
        packSettings ++
        Seq(
          packMain := Map(
            "piper" -> "org.broadinstitute.gatk.queue.QCommandLine",
            "setupFileCreator" -> "molmed.apps.setupcreator.SetupFileCreator",
            "sthlm2UUSNP" -> "molmed.apps.Sthlm2UUSNP",
            "reportParser" -> "molmed.apps.ReportParser"))
          ++ dependencies)
    .configs(PipelineTestRun)
    .settings(inConfig(PipelineTestRun)(Defaults.testTasks): _*)

  lazy val PipelineTestRun = config("pipelinetestrun").extend(Test)

  val dependencies = Seq(
    libraryDependencies += "commons-lang" % "commons-lang" % "2.5",
    libraryDependencies += "org.testng" % "testng" % "5.14.1",
    libraryDependencies += "log4j" % "log4j" % "1.2.16",
    libraryDependencies += "commons-io" % "commons-io" % "2.1",
    libraryDependencies += "net.java.dev.jets3t" % "jets3t" % "0.8.1",
    libraryDependencies += "org.simpleframework" % "simple-xml" % "2.0.4",
    libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0")
}
