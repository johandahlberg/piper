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

  val parser = new OptionParser[Config]("java -cp <class path to piper.jar> molmed.apps.Sthlm2UUSNP") {
    head("SetupFileCreator", " - A utility program to convert a sthlm style project dir to a ua style one. \n" +
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
  def listSubDirectories(dir: File): Seq[File] = dir.listFiles().filter(p => p.isDirectory())

  def createHardLink(sampleName: String, index: String, lane: Int, readPairNumber: Int, readPair: File, uuSampleFolder: File) = {
    val uuStyleFileName = List(
      sampleName,
      index,
      "L" + GeneralUtils.getZerroPaddedIntAsString(lane, 3),
      "R" + readPairNumber,
      "001.fastq.gz").mkString("_")

    val targetFile = new File(uuSampleFolder + "/" + uuStyleFileName)
    Files.createLink(Paths.get(targetFile.getAbsolutePath()), Paths.get(readPair.getAbsolutePath()))
  }

  def parseSampleInfoFromFileName(file: File) = {
    val fileName = file.getName()

    val splitFileName = fileName.split("_")

    val lane = splitFileName(0).toInt
    val flowCellId = splitFileName(2)
    val indexOfLastPart = splitFileName.indexWhere(s => s.contains(".fastq.gz"))
    val sampleName = splitFileName.slice(3, indexOfLastPart).mkString("_")
    // TODO Ugly hack since we don't know the index used
    val index = "AAAAAA"

    (sampleName, lane, flowCellId, index)
  }

  /**
   * Run the app!
   */
  def runApp(config: Config): Unit = {

    for (sampleDir <- listSubDirectories(config.sthlmRoot.get)) {
      for (runfolder <- listSubDirectories(sampleDir)) {

        // Create the ua style runfolder
        val uppsalaStyleRunfolder = new File(config.newUppsalaStyleRoot.get + "/" + runfolder.getName())
        uppsalaStyleRunfolder.mkdirs()

        // Create the sample folders
        val fastqFiles = GeneralUtils.getFileTree(runfolder).
          filter(p => p.getName().contains(".fastq.gz")).
          sortBy(f => f.getName())

        val (sampleName, lane, flowCellId, index) = parseSampleInfoFromFileName(fastqFiles.head)

        createHardLink(sampleName, index, lane, 1, fastqFiles(0), uppsalaStyleRunfolder)
        createHardLink(sampleName, index, lane, 2, fastqFiles(1), uppsalaStyleRunfolder)

        // Write/append to the report file.
        val reportFile = new File(uppsalaStyleRunfolder + "/report.tsv")
        val reportWasThere = reportFile.exists()
        val reportWriter = new PrintWriter(new FileWriter(reportFile, true))

        // Only write the header if the file was just created.
        if (!reportWasThere)
          reportWriter.println(List("#SampleName", "Lane", "ReadLibrary", "FlowcellId").mkString("\t"))

        // Use the sample name as a proxy for the library
        reportWriter.println(List(sampleName, lane, sampleName, flowCellId).mkString("\t"))

        reportWriter.close()
      }
    }
  }

}