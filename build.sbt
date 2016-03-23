
import com.typesafe.sbt.SbtNativePackager.packageArchetype
import NativePackagerHelper._

name in Global := "Piper"
organization := "molmed"

version in Global := "v1.3.0"
scalaVersion in Global := "2.10.1"

scalacOptions in Compile ++= Seq("-deprecation","-unchecked")

lazy val dependencies =
  Seq(
    "commons-lang" % "commons-lang" % "2.5",
    "org.testng" % "testng" % "5.14.1" % "test",
    "log4j" % "log4j" % "1.2.16",
    "commons-io" % "commons-io" % "2.1",
    "net.java.dev.jets3t" % "jets3t" % "0.8.1",
    "org.simpleframework" % "simple-xml" % "2.0.4",
    "com.github.scopt" %% "scopt" % "3.2.0")

lazy val commonSettings =
  packageArchetype.java_application ++
    Seq(libraryDependencies ++= dependencies) ++
    Seq(
      maintainer := "Johan Dahlberg (johan.dahlberg@medsci.uu.se)",
      packageDescription :=
        "Piper is a pipeline engine built on top GATK Queue, aiming at providing workflows for" +
          "a number of NGS related analysis workflows.",
      rpmVendor in Rpm := "National Genomics Infrastructure",
      packageSummary := "NGS workflows",
      rpmLicense in Rpm := Some("MIT")
    )


lazy val root = Project(
  id = "root",
  base = file("."),
  aggregate = Seq(piper, setupCreator)
).dependsOn(piper, setupCreator)


lazy val piper = Project(
  id = "piper",
  base = file("piper"),
  settings = commonSettings ++ piperSettings
)

val resourceBasePath = "piper/src/main/resources/"

lazy val piperSettings = Seq(
  mainClass in Compile := Some("org.broadinstitute.gatk.queue.QCommandLine"),

  // we do not want to include the qscripts in the jar-file
  // but we still want to distribute them.
  mappings in (Compile, packageBin) ~=
    (_ filter { case (f, s) => !s.contains("molmed/qscripts") }),

  mappings in Universal ++= {
    directory("piper/src/main/scala/molmed/qscripts")
  },

  mappings in Universal ++= {
    directory(resourceBasePath + "conf/")
  },

  mappings in Universal ++= {
    directory(resourceBasePath + "workflows/")
  }

)

lazy val setupCreator = Project(
  id = "setupFileCreator",
  base = file("setup-file-creator"),
  settings = commonSettings ++ {
    mainClass in Compile := Some("molmed.apps.setupcreator.SetupFileCreator")
  }
).dependsOn(piper)

