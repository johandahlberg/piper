package molmed.apps.setupcreator

import java.io.File

import ConsoleInputParser.getSingleInput
import ConsoleInputParser.packOptionalValue
import ConsoleInputParser.withDefaultValue
import scopt.OptionParser

/**
 * A simple application to create a setup XML file.
 */
object SetupFileCreator extends App {

  case class Config(
    interactive: Boolean = false,
    outputFile: Option[File] = None)

  val parser = new OptionParser[Config]("java -cp <class path to piper.jar> molmed.apps.SetupFileCreator") {
    head("SetupFileCreator", " - A utility program to create pipeline setup xml files for Piper..")

    opt[Unit]('i', "interactive") optional () valueName ("Start the SetupFileCreator in interactive mode") action { (x, c) =>
      c.copy(interactive = true)
    } text ("This is a optional argument.")

    opt[File]('o', "output") required () valueName ("Output xml file.") action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text ("This is a required argument.")
  }

  // Start up the app!
  parser.parse(args, new Config()) map { config =>

    if (config.interactive)
      runInInteractiveMode(config)
    else
      runNonInteractiveMode(config)

  } getOrElse {
    // arguments are bad, usage message will have been displayed
  }

  def runNonInteractiveMode(config: Config): Unit = {
    ???
  }

  def runInInteractiveMode(config: Config): Unit = {

    // Contains the get input method and String => Option[String] conversion.
    import ConsoleInputParser._

    val project = SetupUtils.createProject()

    // -------------------------------------------------
    // Get meta data from console
    // -------------------------------------------------

    val projectName = getSingleInput("Project name")
    val seqencingPlatform = withDefaultValue("Sequencing platform", defaultValue = "Illumina")(getSingleInput)
    val sequencingCenter = withDefaultValue("Sequencing center", defaultValue = "UU-SNP")(getSingleInput)
    val uppmaxProjectId = withDefaultValue("Uppmax project id", defaultValue = "a2009002")(getSingleInput)
    val uppmaxQoSFlag = withDefaultValue("Uppmax QoS flag (default is no flag)", defaultValue = "")(getSingleInput)

    val projectWithMetaData =
      SetupUtils.setMetaData(project)(
        projectName,
        seqencingPlatform,
        sequencingCenter,
        uppmaxProjectId,
        uppmaxQoSFlag)

    def getReference: File = {
      val reference = new File(withDefaultValue("Reference",
        defaultValue = "/proj/b2010028/references/piper_references/gatk_bundle/2.2/b37/human_g1k_v37.fasta")(getSingleInput))

      try {
        require(reference.exists(), "Cannot find reference: " + reference)
      } catch {
        case ie: IllegalArgumentException => {
          println("Cannot find reference: " + reference + ". Input a correct reference.")
          getReference
        }

      }
      reference
    }

    val reference = getReference

    // -------------------------------------------------
    // Setup run folders
    // -------------------------------------------------

    def getRunFoldersFromRootDir(): Seq[File] = {
      val rootDir = new File(getSingleInput(Option("Path to the run folder root dir")))
      require(rootDir.isDirectory(), rootDir + " was not a directory.")
      rootDir.listFiles().filter(_.isDirectory)
    }

    SetupUtils.setReports(project)(getRunFoldersFromRootDir())

    // -------------------------------------------------
    // Setup sample folders
    // -------------------------------------------------

    SetupUtils.setSamplesAndReference(project)(reference)

    println("Finished setting up project. Writing project xml to " + config.outputFile.get.getAbsolutePath() + " now.")
    SetupUtils.writeToFile(project, config.outputFile.get)
  }
}