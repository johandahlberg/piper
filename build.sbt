
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Files.copy

import com.typesafe.sbt.SbtNativePackager.packageArchetype
import NativePackagerHelper._

import com.typesafe.sbt.SbtGit.GitKeys._


/**
  * General options
  */
name in Global := "Piper"
organization := "molmed"

scalaVersion in Global := "2.10.3"
scalacOptions in Compile ++= Seq("-deprecation","-unchecked")

val gatkVersionHash = "eee94ec81f721044557f590c62aeea6880afd927"

/**
  * We use git to handle versioning - the latest tag will be used as version.
  */
enablePlugins(GitVersioning)
enablePlugins(GitBranchPrompt)

git.uncommittedSignifier := Some("SNAPSHOT")
git.useGitDescribe := true
git.gitTagToVersionNumber := gitDescribeToVersion

val versionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

def gitDescribeToVersion(s: String): Option[String] = {
  s match {
    case versionRegex(v,"SNAPSHOT") => Some(s"$v")
    case versionRegex(v,"") => Some(v)
    case versionRegex(v,s) => Some(s"$v-$s")
    case v => None
  }
}

/**
  * Handled dependencies
  */
lazy val dependencies =
  Seq(
    "commons-lang" % "commons-lang" % "2.5",
    "org.testng" % "testng" % "5.14.1" % "test",
    "log4j" % "log4j" % "1.2.16",
    "commons-io" % "commons-io" % "2.1",
    "net.java.dev.jets3t" % "jets3t" % "0.8.1",
    "org.simpleframework" % "simple-xml" % "2.0.4",
    "com.github.scopt" %% "scopt" % "3.2.0")

/**
  * We need to download and build the GATK the first time
  * that piper is to be built.
  */

lazy val downloadGATK = settingKey[List[File]]("Clone GATK from github.")

downloadGATK in Global := {

  val gatkDir = file("gatk-protected")
  val gatkJar = file(gatkDir + "/target/GenomeAnalysisTK.jar")
  val queueJar = file(gatkDir + "/target/Queue.jar")
  val lib = file("piper/lib")

  val queueInLib = file(lib + "/" + queueJar.getName).exists()
  val gatkInLib = file(lib + "/" + gatkJar.getName).exists()

  if(queueInLib && gatkInLib){
    println("GATK jar found - will skip download and build.")
  }
  else {
    println("Didn't find GATK jar - will download and build it.")

    Process("git clone https://github.com/broadgsa/gatk-protected.git") #&&
      Process(s"git checkout $gatkVersionHash", gatkDir) #&&
      Process("mvn package", gatkDir) !!

    if(!lib.exists())
      lib.mkdirs()
  }

  def copySourceWithName(source: File, targetDir: File): Path = {
    copy(source.toPath, targetDir.toPath.resolve(source.getName), REPLACE_EXISTING)
  }

  val copiedGATKJar = copySourceWithName(gatkJar, lib)
  val copiedQueueJar = copySourceWithName(queueJar, lib)
  List(copiedGATKJar.toFile, copiedQueueJar.toFile)
}

unmanagedJars in Compile ++= downloadGATK.value

/**
  * Settings in common for both modules
  */

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


/**
  * Settings specific to the Piper module
  */

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

/**
  * Settings specific to the setup-file-creator module.
  */

lazy val setupCreator = Project(
  id = "setupFileCreator",
  base = file("setup-file-creator"),
  settings = commonSettings ++ {
    mainClass in Compile := Some("molmed.apps.setupcreator.SetupFileCreator")
  }
).dependsOn(piper)

