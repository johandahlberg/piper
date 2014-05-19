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
          (_ filter { case (f, s) => !s.contains("molmed/qscripts") }),
        parallelExecution in Test := false) ++
        packSettings  ++ 
        Seq(
        // [Optional] Specify mappings from program name -> Main class (full package path)
        packMain := Map("piper" -> "org.broadinstitute.sting.queue.QCommandLine",
            "setupFileCreator" ->"molmed.apps.SetupFileCreator")
        // Add custom settings here
        // [Optional] JVM options of scripts (program name -> Seq(JVM option, ...))
        //packJvmOpts := Map("hello" -> Seq("-Xmx512m")),
        // [Optional] Extra class paths to look when launching a program
        //packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/etc")), 
        // [Optional] (Generate .bat files for Windows. The default value is true)
        //packGenerateWindowsBatFile := true
        // [Optional] jar file name format in pack/lib folder (Since 0.5.0)
        //   "default"   (project name)-(version).jar 
        //   "full"      (organization name).(project name)-(version).jar
        //   "no-version" (organization name).(project name).jar
        //   "original"  (Preserve original jar file names)
        //packJarNameConvention := "default",
        // [Optional] List full class paths in the launch scripts (default is false) (since 0.5.1)
        //packExpandedClasspath := false
      ) 
        ++ dependencies)
    .configs(PipelineTestRun)
    .settings(inConfig(PipelineTestRun)(Defaults.testTasks): _*)

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
