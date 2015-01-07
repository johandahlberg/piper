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
    outputFile: Option[File] = None,
    projectName: Option[String] = None,
    seqencingPlatform: Option[String] = None,
    sequencingCenter: Option[String] = None,
    uppmaxProjectId: Option[String] = None,
    uppmaxQoSFlag: Option[String] = Some(""),
    fastqFiles: Option[Set[File]] = None,
    reference: Option[File] = None)

  val parser = new OptionParser[Config]("SetupFileCreator") {
    head("SetupFileCreator", " - A utility program to create pipeline setup xml files for Piper..")

    opt[File]('o', "output") required () valueName ("Output xml file.") action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text ("This is a required argument.")

    opt[String]('p', "project_name") optional () valueName ("The name of this project.") action { (x, c) =>
      c.copy(projectName = Some(x))
    } text ("This is a required argument.")

    opt[String]('s', "sequencing_platform") optional () valueName ("The technology used for sequencing, e.g. Illumina") action { (x, c) =>
      c.copy(seqencingPlatform = Some(x))
    } text ("This is a required argument.")

    opt[String]('c', "sequencing_center") optional () valueName ("Where the sequencing was carried out, e.g. NGI") action { (x, c) =>
      c.copy(sequencingCenter = Some(x))
    } text ("This is a required argument.")

    opt[String]('q', "qos") optional () valueName ("A optional quality of service (QoS) flag to forward to the cluster.") action { (x, c) =>
      c.copy(uppmaxQoSFlag = Some(x))
    } text ("This is a optional argument.")

    opt[String]('a', "uppnex_project_id") optional () valueName ("The uppnex project id to charge the core hours to.") action { (x, c) =>
      c.copy(uppmaxProjectId = Some(x))
    } text ("This is a required argument.")

    opt[File]('i', "input_fastq") unbounded () optional () valueName ("Input path to fastq files to include in analysis.") action { (x, c) =>
      c.copy(fastqFiles = c.fastqFiles.getOrElse(Set()) + x)
    } text ("This is a required argument. Can be specified multiple times.")

    opt[File]('r', "reference") optional () valueName ("Reference fasta file to use.") action { (x, c) =>
      c.copy(reference = Some(x))
    } text ("This is a required argument.")
  }

  // Start up the app!
  parser.parse(args, new Config()) map { config =>

    val allFieldsAreSet =
      config.getClass().getDeclaredFields.
        forall(p => p.isDefined)

    if (allFieldsAreSet)
      createSetupFile(config)
    else {
      parser.showUsage
      System.exit(1)
    }
      
      

  } getOrElse {
    // arguments are bad, usage message will have been displayed
    System.exit(1)
  }

  def createSetupFile(config: Config): Unit = {

    val project = SetupUtils.createProject()

    val projectWithMetaData = SetupUtils.setMetaData(project)(
      config.projectName.get,
      config.seqencingPlatform.get,
      config.sequencingCenter.get,
      config.uppmaxProjectId.get,
      config.uppmaxQoSFlag.get,
      config.reference.get)

    val (uuFiles, ignFiles) =
      SetupUtils.splitByFormatType(config.fastqFiles.get)

    val projectWithUUSamplesAdded =
      SetupUtils.createXMLFromUUHierarchy(projectWithMetaData)(uuFiles)

    val projectWithIGNSamplesAdded =
      SetupUtils.createXMLFromIGNHierarchy(projectWithUUSamplesAdded)(ignFiles)

    SetupUtils.writeToFile(projectWithIGNSamplesAdded, config.outputFile.get)

    println("Successfully created: " + config.outputFile.get + ".")
  }

}