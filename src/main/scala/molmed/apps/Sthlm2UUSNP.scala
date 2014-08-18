package molmed.apps

import java.io.File
import scala.io.Source
import molmed.utils.GeneralUtils
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.FileAlreadyExistsException
import scopt._
import java.io.FileWriter

/**
 * Utility program to convert sthlm sequencing platform meta format to UU SNP format.
 */
object Sthlm2UUSNP extends App {

  case class Config(
    sthlmRoot: Option[File] = None,
    newUppsalaStyleRoot: Option[File] = None,
    flowcells: Seq[String] = Seq())

  val parser = new OptionParser[Config]("sthlm2UUSNP") {
    head("sthlm2UUSNP", " - A utility program to convert a sthlm style project dir to a ua style one. \n" +
      "Run example: " +
      "./sthlm2UUSNP " +
      " --input_root <sthlm project root folder> " +
      " --out_root <root of uppsala style project>")

    opt[File]('i', "input_root") required () valueName ("A sthlm style project directory.") action { (x, c) =>
      c.copy(sthlmRoot = Some(x))
    } text ("This is a required argument.")

    opt[File]('o', "out_root") required () valueName ("Root dir where the ua style directories will be written.") action { (x, c) =>
      c.copy(newUppsalaStyleRoot = Some(x))
    } text ("This is a required argument.")

    opt[String]('f', "flowcell") unbounded () optional () valueName ("Data format conversion will be restricted to the following flowcells.") action { (x, c) =>
      c.copy(flowcells = c.flowcells :+ x)
    } text ("This is a optional argument, and it can be specified multiple times.")

  }

  // Start up the app!
  parser.parse(args, new Config()) map { config =>

    runApp(config)

  } getOrElse {
    // arguments are bad, usage message will have been displayed
  }

  /**
   * Utility functions
   */

  /**
   * List all subdirectories of dir
   */
  def listSubDirectories(dir: File): Seq[File] = {

    require(dir.isDirectory(), dir + " was not a directory!")

    val subDirectories = dir.listFiles().filter(p => p.isDirectory())
    assert(subDirectories.size > 0,
      "Found no subdirectories for: " + dir.getAbsolutePath())

    subDirectories
  }

  /**
   * Create hardlink corresponding to sequencing unit
   * @param sampleInfo		The sequencing unit
   * @param uuSampleFolder	Folder to output to
   * @return the target file
   */
  def createHardLink(
    sampleInfo: SampleInfo,
    uuRunfolderFolder: File): File = {

    val uuSampleFolder =
      new File(uuRunfolderFolder + "/" + "Sample_" + sampleInfo.sampleName)

    uuSampleFolder.mkdirs()

    val uuStyleFileName = List(
      sampleInfo.sampleName,
      sampleInfo.index,
      "L" + GeneralUtils.getZerroPaddedIntAsString(sampleInfo.lane, 3),
      "R" + sampleInfo.read,
      "001.fastq.gz").mkString("_")

    val targetFile = new File(uuSampleFolder + "/" + uuStyleFileName)

    try {
      Files.createLink(Paths.get(targetFile.getAbsolutePath()),
        Paths.get(sampleInfo.fastq.getPath()))
    } catch {
      case e: FileAlreadyExistsException =>
        System.err.println(
          "File " + targetFile.getName() + " already exists." +
            " Will not re-link it.")
    }

    targetFile
  }

  /**
   * Holds information on the Sample and file files associated with it.
   */
  case class SampleInfo(
    sampleName: String,
    library: String,
    lane: Int,
    date: String,
    flowCellId: String,
    index: String,
    fastq: File,
    read: Int)

  /**
   * The Stockholm folder structure looks like this:
   *
   * Project
   * └── Sample
   *     └── Library Prep
   *         └── Sequencing Run
   *             ├── P1142_101_NoIndex_L002_R1_001.fastq.gz
   *             └── P1142_101_NoIndex_L002_R1_001.fastq.gz
   *
   * And this should be parsed to get info on the sample
   *
   *
   * @param file 	  		Fastq file on the format described above.
   * @param runFolder 		The runfoler where the fastq file is located with a
   * 				  		name conforming to the specification above.
   * @param libraryPrepDir  The location of the libary pre dir
   * @return Information on the sequencing unit parsed from the file name
   */
  def parseSampleInfoFromFileHierarchy(
    file: File,
    runfolder: File,
    libraryPrepDir: File): SampleInfo = {

    val libraryPrepName = libraryPrepDir.getName()

    def getDataAndFlowcellIdFromRunfolder(runfolder: File): (String, String) = {
      val split = runfolder.getName().split("_")
      (split(0), split(split.length - 1))
    }

    val runfolderName = runfolder.getName()
    val (date, flowcellId) = getDataAndFlowcellIdFromRunfolder(runfolder)

    val fileName = file.getName()
    val fastqFileRegexp =
      """^(\w+_\w+)_(\w+(?:-\w+)?)_L(\d+)_R(\d)_(\d+)\.fastq\.gz$""".r

    val infoAboutSamples = fastqFileRegexp.findAllIn(fileName).
      matchData.map(m => {
        SampleInfo(
          sampleName = m.group(1),
          library = libraryPrepName,
          lane = m.group(3).toInt,
          date = date,
          flowCellId = flowcellId,
          index = m.group(2),
          fastq = file,
          read = m.group(4).toInt)
      }).toSeq

    require(
      infoAboutSamples.length == 1,
      "Just one sample hit should be possible for regexp, found: " +
        infoAboutSamples.length)

    infoAboutSamples(0)
  }

  /**
   * Get all the fastq files from a runfolder (in the sthlm format)
   * as a Stream
   * @param runfolder
   * @return A stream of fastq files sorted by name
   */
  def getFastqFiles(runfolder: File): Stream[File] = {
    val fastqFiles = GeneralUtils.getFileTree(runfolder).
      filter(p => p.getName().contains(".fastq.gz")).
      sortBy(f => f.getName())
    fastqFiles
  }

  /**
   * Add information on the sequencedUnit to the report
   * If the report file already exists it will remove the
   * report file and create a new one.
   * @param sequencedUnit			The Sample info on the sequenced unit
   * @param uppsalaStyleRunfolder	The runfolder to add the report to.
   * @return the report that was written to.
   */
  def addToReport(
    sequencedUnits: Seq[SampleInfo],
    uppsalaStyleRunfolder: File): File = {

    // Write to the report file.
    val reportFile = new File(uppsalaStyleRunfolder + "/report.tsv")
    val reportAlreadyExists = reportFile.exists()

    if (reportAlreadyExists) {
      System.err.println("There is already a copy of " + reportFile +
        " will delete and replace it now.")
      reportFile.delete()
    }

    val reportWriter =
      new PrintWriter(
        new FileWriter(reportFile))

    reportWriter.println(
      List(
        "#SampleName",
        "Lane",
        "ReadLibrary",
        "FlowcellId")
        .mkString("\t"))

    for (sequencedUnit <- sequencedUnits) {
      reportWriter.println(
        List(
          sequencedUnit.sampleName,
          sequencedUnit.lane,
          sequencedUnit.library,
          sequencedUnit.flowCellId)
          .mkString("\t"))
    }

    reportWriter.close()

    reportFile
  }

  def shouldBeIncluded(config: Config, runfolderDir: File): Boolean = {
    if (config.flowcells.isEmpty)
      true
    else
      config.flowcells.exists(flowcell => flowcell == runfolderDir.getName())
  }

  /**
   * Generate a Uppsala file structure from the IGN-sthlm format.
   * @param config The config for what to run
   * @return A map of the runfolders and the sample information relating to
   * them. This can be used to generate the report files.
   */
  def generateFileStructure(config: Config): Map[File, Seq[SampleInfo]] = {
    // Iterate through the sthlm sample, library perp and runfolder 
    // dirs to get to the fastq files.
    var runfolderToSampleMap: Map[File, Seq[SampleInfo]] =
      Map().withDefaultValue(Seq())

    for (sampleDir <- listSubDirectories(config.sthlmRoot.get)) {
      for (libraryPrepDir <- listSubDirectories(sampleDir)) {
        for {
          runfolderDir <- listSubDirectories(libraryPrepDir)
          if shouldBeIncluded(config, runfolderDir)
        } {

          // Get the information on the samples and add them to the
          // ua style runfolder
          val fastqFiles = getFastqFiles(runfolderDir)
          val infoOnSamples =
            fastqFiles.map(file =>
              parseSampleInfoFromFileHierarchy(
                file,
                runfolderDir,
                libraryPrepDir))

          // Create the ua style runfolder       
          val uppsalaStyleRunfolder =
            new File(config.newUppsalaStyleRoot.get +
              "/" + runfolderDir.getName())
          uppsalaStyleRunfolder.mkdirs()

          for (sequencedUnit <- infoOnSamples) {
            createHardLink(sequencedUnit, uppsalaStyleRunfolder)
          }

          runfolderToSampleMap = runfolderToSampleMap.
            updated(
              uppsalaStyleRunfolder,
              runfolderToSampleMap(uppsalaStyleRunfolder) ++ infoOnSamples)

        }
      }
    }

    runfolderToSampleMap

  }

  def createReportFiles(
    runfoldersToSampleMap: Map[File, Seq[SampleInfo]]): Seq[File] = {

    val reportFiles =
      for (runfolder <- runfoldersToSampleMap.keys) yield {
        val infoOnSamples = runfoldersToSampleMap(runfolder)
        val onlyReadOne = infoOnSamples.filter(p => p.read == 1)
        addToReport(onlyReadOne, runfolder)
      }

    reportFiles.toSeq
  }

  /**
   * Run the app!
   */
  def runApp(config: Config): Unit = {
    val runfoldersToSampleMap = generateFileStructure(config)
    val reportFiles = createReportFiles(runfoldersToSampleMap)
  }

}