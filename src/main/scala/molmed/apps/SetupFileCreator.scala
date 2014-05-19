package molmed.apps

import javax.xml.bind.JAXBContext
import molmed.xml.setup.Samplefolder
import molmed.xml.setup.Project
import molmed.xml.setup.Metadata
import molmed.xml.setup.Inputs
import java.io.FileOutputStream
import javax.xml.bind.Marshaller
import java.io.File
import collection.JavaConversions._
import molmed.xml.setup.Project
import molmed.xml.setup.Runfolder
import scopt._

/**
 * A simple application to create a setup XML file.
 */
object SetupFileCreator extends App {
  
  case class Config(
    interactive: Boolean = false,
    outputFile: Option[File] = None)

  val parser = new OptionParser[Config]("java -cp <class path to piper.jar> molmed.apps.SetupFileCreator") {
    head("SetupFileCreator", " - A utility program to create pipeline setup xml files for Piper..")    

    opt[Boolean]('i', "interactive") optional () valueName ("Start the SetupFileCreator in interactive mode") action { (x, c) =>
      c.copy(interactive = x)
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

    // The xml marshaller is used to create the xml instance
    val context = JAXBContext.newInstance(classOf[Project])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val project = new Project

    // -------------------------------------------------
    // Create meta data
    // -------------------------------------------------

    val projectMetaData = new Metadata()

    val projectName = getSingleInput("Project name")
    val seqencingPlatform = withDefaultValue("Sequencing platform", defaultValue = "Illumina")(getSingleInput)
    val sequencingCenter = withDefaultValue("Sequencing center", defaultValue = "UU-SNP")(getSingleInput)
    val uppmaxProjectId = withDefaultValue("Uppmax project id", defaultValue = "a2009002")(getSingleInput)
    val uppmaxQoSFlag = withDefaultValue("Uppmax QoS flag (default is no flag)", defaultValue = "")(getSingleInput)

    projectMetaData.setName(projectName)
    projectMetaData.setPlatfrom(seqencingPlatform)
    projectMetaData.setSequenceingcenter(sequencingCenter)
    projectMetaData.setUppmaxprojectid(uppmaxProjectId)
    projectMetaData.setUppmaxqos(uppmaxQoSFlag)
    project.setMetadata(projectMetaData)

    project.setInputs(new Inputs)

    def getReference: String = {
      val reference = new File(withDefaultValue("Reference",
        defaultValue = "/proj/b2010028/references/piper_references/gatk_bundle/2.2/b37/human_g1k_v37.fasta")(getSingleInput)).getAbsolutePath()

      try {
        require(new File(reference).exists(), "Cannot find reference: " + reference)
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

    def getRunFoldersFromRootDir(): List[String] = {
      val rootDir = new File(getSingleInput(Option("Path to the run folder root dir")))
      require(rootDir.isDirectory(), rootDir + " was not a directory.")
      rootDir.listFiles().filter(_.isDirectory).toList.map(f => f.getAbsolutePath())
    }

    val runFolderList = project.getInputs().getRunfolder()

    val runFolderPathList = getRunFoldersFromRootDir()

    runFolderList.addAll(runFolderPathList.map(path => {

      def lookForReport(p: String): String = {
        val dir = new File(p)
        require(dir.isDirectory(), dir + " was not a directory.")
        val reportFile: File = dir.listFiles().find(report => report.getName() == "report.xml" || report.getName() == "report.tsv").getOrElse(throw new Error("Could not find report.xml in " + dir.getPath()))
        reportFile.getAbsolutePath()
      }

      val runFolder = new Runfolder
      runFolder.setReport(lookForReport(path))
      runFolder

    }))

    // -------------------------------------------------
    // Setup sample folders
    // -------------------------------------------------

    runFolderList.map(runFolder => {
      val sampleFolderList = runFolder.getSamplefolder()
      val runFolderPath = new File(runFolder.getReport.get).getParentFile()
      val sampleFolders = runFolderPath.listFiles().filter(s => s.getName().startsWith("Sample_")).toList

      val sampleFolderInstances = sampleFolders.map(sampleFolder => {
        val sample = new Samplefolder
        sample.setName(sampleFolder.getName().replace("Sample_", ""))
        sample.setPath(sampleFolder.getAbsolutePath())
        sample.setReference(reference)
        sample
      })

      sampleFolderList.addAll(sampleFolderInstances)
    })

    println("Finished setting up project. Writing project xml to " + config.outputFile.get.getAbsolutePath() + " now.")
    marshaller.marshal(project, new FileOutputStream(config.outputFile.get))
  }
}

object ConsoleInputParser {
  implicit def packOptionalValue[T](value: T): Option[T] = Some(value)

  private def checkInput[T](function: Option[String] => T)(key: Option[String], value: T, checkInputQuestion: Option[String]): T = {
    val valid = readLine(checkInputQuestion.get + "\n")
    valid match {
      case "y" => value
      case "n" => function(key)
      case _ => {
        println("Did not recognize input: " + valid)
        checkInput(function)(key, value, checkInputQuestion)
      }
    }
  }

  def getMultipleInputs(key: Option[String]): List[String] = {

    def continue(accumulator: List[String]): List[String] = {

      val value = readLine("Set " + key.get + ":" + "\n")
      checkInput[List[String]](getMultipleInputs)(key, List(value), "Value of key: " + key.get + ", was set to: " + value + ". Do you want to keep it? [y/n]")

      val cont = readLine("Do you want to add another " + key.get + "? [y/n]" + "\n")
      cont match {
        case "n" => value :: accumulator
        case "y" => continue(value :: accumulator)
        case _ => {
          println("Did not recognize input: " + cont)
          continue(accumulator)
        }
      }
    }

    continue(List())
  }

  def withDefaultValue[T](key: Option[String], defaultValue: T)(function: Option[String] => T): T = {
    if (defaultValue.isDefined)
      checkInput(function)(key, defaultValue.get, "The default value of " + key.get + " is " + defaultValue.get + ". Do you want to keep it? [y/n]")
    else
      function(key)
  }

  def getSingleInput(key: Option[String]): String = {
    val value = readLine("Set " + key.get + ":" + "\n")
    checkInput[String](getSingleInput)(key, value, "Value of key: " + key.get + ", was set to: " + value + ". Do you want to keep it? [y/n]")
  }
}