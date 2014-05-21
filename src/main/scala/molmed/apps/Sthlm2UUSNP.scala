package molmed.apps

import java.io.File
import scala.io.Source
import molmed.utils.GeneralUtils
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths

import scopt._

/**
 * Utility program to convert sthlm sequencing platform meta format to UU SNP format.
 */
object Sthlm2UUSNP extends App {

  case class Config(sthlmRoot: Option[File] = None, newUppsalaStyleRoot: Option[File] = None)

  val parser = new OptionParser[Config]("java -cp <class path to piper.jar> molmed.apps.Sthlm2UUSNP") {
    head("SetupFileCreator", " - A utility program to create pipeline setup xml files for Piper..")

    opt[File]('i', "input_root") required () valueName ("Root dir of a sthlm style directory.") action { (x, c) =>
      c.copy(sthlmRoot = Some(x))
    } text ("This is a required argument.")

    opt[File]('o', "out_root") required () valueName ("Root dir where the ua style directory will be written.") action { (x, c) =>
      c.copy(newUppsalaStyleRoot = Some(x))
    } text ("This is a required argument.")
  }

  // Start up the app!
  parser.parse(args, new Config()) map { config =>

    runApp(config)
    
  } getOrElse {
    // arguments are bad, usage message will have been displayed
  }

  def runApp(config: Config): Unit = {

    config.newUppsalaStyleRoot.get.mkdirs()
    val reportFile = new File(config.newUppsalaStyleRoot.get + "/report.tsv")
    val reportWriter = new PrintWriter(reportFile)

    reportWriter.println(List("#SampleName", "Lane", "ReadLibrary", "FlowcellId").mkString("\t"))

    val files = GeneralUtils.getFileTree(config.sthlmRoot.get).
      filter(p => p.getName().contains(".fastq.gz")).
      sortBy(f => f.getName())

    // Group them to get the read pairs together
    for (file <- files.grouped(2)) {

      // 1_131129_AH7W5YADXX_P700_401_2.fastq.gz
      val fileName = file(0).getName()

      val splitFileName = fileName.split("_")

      val lane = splitFileName(0).toInt
      val flowCellId = splitFileName(2)
      val indexOfLastPart = splitFileName.indexWhere(s => s.contains(".fastq.gz"))
      val sampleName = splitFileName.slice(3, indexOfLastPart).mkString("_")
      // TODO Ugly hack since we don't know the index used
      val index = "AAAAAA"

      // Create Sample folders    
      val uuSampleFolder = new File(config.newUppsalaStyleRoot.get + "/Sample_" + sampleName + "/")
      uuSampleFolder.mkdirs()

      // Create link hard links for the files
      def createHardLink(readPair: File, readPairNumber: Int) = {
        val uuStyleFileName = List(
          sampleName,
          index,
          "L" + GeneralUtils.getZerroPaddedIntAsString(lane, 3),
          "R" + readPairNumber,
          "001.fastq.gz").mkString("_")

        val targetFile = new File(uuSampleFolder + "/" + uuStyleFileName)
        Files.createLink(Paths.get(targetFile.getAbsolutePath()), Paths.get(readPair.getAbsolutePath()))
      }

      createHardLink(file(0), 1)
      createHardLink(file(1), 2)

      reportWriter.println(List(sampleName, lane, sampleName, flowCellId).mkString("\t"))

    }
    reportWriter.close()

  }

}