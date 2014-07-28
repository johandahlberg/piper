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
    interactive: Option[Boolean] = Some(false),
    outputFile: Option[File] = None,
    projectName: Option[String] = None,
    seqencingPlatform: Option[String] = None,
    sequencingCenter: Option[String] = None,
    uppmaxProjectId: Option[String] = None,
    uppmaxQoSFlag: Option[String] = Some(""),
    sampleFolders: Option[Set[File]] = None,
    reference: Option[File] = None)

  val parser = new OptionParser[Config]("SetupFileCreator") {
    head("SetupFileCreator", " - A utility program to create pipeline setup xml files for Piper..")

    opt[Unit]('x', "interactive") optional () valueName ("Start the SetupFileCreator in interactive mode") action { (x, c) =>
      c.copy(interactive = true)
    } text ("This is a optional argument.")

    opt[File]('o', "output") required () valueName ("Output xml file.") action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text ("This is a required argument.")

    opt[String]('p', "project_name") optional () valueName ("The name of this project.") action { (x, c) =>
      c.copy(projectName = Some(x))
    } text ("This is a required argument if you are not using interactive mode.")

    opt[String]('s', "sequencing_platform") optional () valueName ("The technology used for sequencing, e.g. Illumina") action { (x, c) =>
      c.copy(seqencingPlatform = Some(x))
    } text ("This is a required argument if you are not using interactive mode.")

    opt[String]('c', "sequencing_center") optional () valueName ("Where the sequencing was carried out, e.g. NGI") action { (x, c) =>
      c.copy(sequencingCenter = Some(x))
    } text ("This is a required argument if you are not using interactive mode.")

    opt[String]('a', "uppnex_project_id") optional () valueName ("The uppnex project id to charge the core hours to.") action { (x, c) =>
      c.copy(uppmaxProjectId = Some(x))
    } text ("This is a required argument if you are not using interactive mode.")

    opt[File]('i', "input_sample") unbounded () optional () valueName ("Input path to sample directory.") action { (x, c) =>
      c.copy(sampleFolders = c.sampleFolders.getOrElse(Set()) + x)
    } text ("This is a required argument if you are not using interactive mode. Can be specified multiple times.")

    opt[File]('r', "reference") optional () valueName ("Reference fasta file to use.") action { (x, c) =>
      c.copy(reference = Some(x))
    } text ("This is a required argument if you are not using interactive mode.")

  }

  // Start up the app!
  parser.parse(args, new Config()) map { config =>

    val allFieldsAreSet = config.getClass().getDeclaredFields.forall(p => p.isDefined)

    if (allFieldsAreSet)
      if (config.interactive.get)
        runInInteractiveMode(config)
      else
        runNonInteractiveMode(config)
    else
      parser.showUsage

  } getOrElse {
    // arguments are bad, usage message will have been displayed
  }

  def runNonInteractiveMode(config: Config): Unit = {

    val project = SetupUtils.createProject()

    val projectWithMetaData = SetupUtils.setMetaData(project)(
      config.projectName.get,
      config.seqencingPlatform.get,
      config.sequencingCenter.get,
      config.uppmaxProjectId.get,
      config.uppmaxQoSFlag.get)

    val projectWithSamples =
      SetupUtils.setupRunfolderStructureFromSamplePaths(projectWithMetaData)(
        config.sampleFolders.get,
        config.reference.get)

    SetupUtils.writeToFile(projectWithSamples, config.outputFile.get)
    
    println("Successfully created: " + config.outputFile.get + ".")
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

    val projectWithRunfolders = SetupUtils.setRunfolders(projectWithMetaData)(getRunFoldersFromRootDir())

    // -------------------------------------------------
    // Setup sample folders
    // -------------------------------------------------

    SetupUtils.setReferenceForSamples(projectWithRunfolders)(reference)

    println("Finished setting up project. Writing project xml to " + config.outputFile.get.getAbsolutePath() + " now.")
    SetupUtils.writeToFile(projectWithRunfolders, config.outputFile.get)
  }
}