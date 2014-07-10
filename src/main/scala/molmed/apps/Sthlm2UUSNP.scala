package molmed.apps

import java.io.File
import scala.io.Source
import molmed.utils.GeneralUtils
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import scopt._
import java.io.FileWriter

/**
 * Utility program to convert sthlm sequencing platform meta format to UU SNP format.
 */
object Sthlm2UUSNP extends App {

  case class Config(sthlmRoot: Option[File] = None, newUppsalaStyleRoot: Option[File] = None)

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
  def listSubDirectories(dir: File): Seq[File] =
    dir.listFiles().filter(p => p.isDirectory())

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
    Files.createLink(Paths.get(targetFile.getAbsolutePath()),
      Paths.get(sampleInfo.fastq.getPath()))
    targetFile
  }

  /**
   * Holds information on the Sample and file files associated with it.
   */
  case class SampleInfo(
    sampleName: String,
    lane: Int,
    date: String,
    flowCellId: String,
    index: String,
    fastq: File,
    read: Int)

  /**
   * Sthlm files look like: 1_140528_BC423WACXX_P1142_101_1.fastq.gz
   * Parse this info to get info about the sample.
   *
   * @param file Fastq file on the format described above.
   * @return Information on the sequencing unit parsed from the file name
   */
  def parseSampleInfoFromFileName(file: File): SampleInfo = {
    val fileName = file.getName()

    val regexp = """^(\d)_(\d+)_(\w+)_(\w+_\w+)_(\d).fastq.gz$""".r

    // @TODO 
    // Note the ugly hack that sets all indicies to AAAAAA
    // this done since we currently lack a good way to get this
    // info.
    val infoAboutSamples = regexp.findAllIn(fileName).matchData.map(m => {
      SampleInfo(
        sampleName = m.group(4),
        lane = m.group(1).toInt,
        date = m.group(2),
        flowCellId = m.group(3),
        index = "AAAAAA",
        fastq = file,
        read = m.group(5).toInt)
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
   * @param sequencedUnit			The Sample info on the sequenced unit
   * @param uppsalaStyleRunfolder	The runfolder to add the report to.
   * @return the report that was written to.
   */
  def addToReport(
    sequencedUnit: SampleInfo,
    uppsalaStyleRunfolder: File): File = {

    // Write/append to the report file.
    val reportFile = new File(uppsalaStyleRunfolder + "/report.tsv")
    val reportWasThere = reportFile.exists()
    val reportWriter = new PrintWriter(new FileWriter(reportFile, true))

    // Only write the header if the file was just created.
    if (!reportWasThere)
      reportWriter.println(
        List(
          "#SampleName",
          "Lane",
          "ReadLibrary",
          "FlowcellId")
          .mkString("\t"))

    // Use the sample name as a proxy for the library
    reportWriter.println(
      List(
        sequencedUnit.sampleName,
        sequencedUnit.lane,
        sequencedUnit.sampleName,
        sequencedUnit.flowCellId)
        .mkString("\t"))

    reportWriter.close()

    reportFile
  }

  /**
   * Run the app!
   */
  def runApp(config: Config): Unit = {

    // Iterate through the sthlm sample and runfolder dirs to get to the
    // fastq files.
    for (sampleDir <- listSubDirectories(config.sthlmRoot.get)) {
      for (runfolder <- listSubDirectories(sampleDir)) {

        // Get the information on the samples and add them to the
        // ua style runfolder
        val fastqFiles = getFastqFiles(runfolder)
        val infoOnSamples =
          fastqFiles.map(file => parseSampleInfoFromFileName(file))

        // Create the ua style runfolder       
        val uppsalaStyleRunfolder =
          new File(config.newUppsalaStyleRoot.get + "/" + runfolder.getName())
        uppsalaStyleRunfolder.mkdirs()

        for (sequencedUnit <- infoOnSamples) {
          createHardLink(sequencedUnit, uppsalaStyleRunfolder)

          // Since we don't want the unit added twice for paired end data
          // only add read 1
          if (sequencedUnit.read == 1)
            addToReport(sequencedUnit, uppsalaStyleRunfolder)
        }
      }
    }
  }

}