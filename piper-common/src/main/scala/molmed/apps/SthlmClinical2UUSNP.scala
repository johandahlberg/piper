package molmed.apps

import java.io.File
import scala.io.Source
import molmed.utils.GeneralUtils
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Utility program to convert sthlm clinical sequencing platform meta format to UU SNP format.
 */
object SthlmClinical2UUSNP extends App {

  val usage = "Missing input file. Usage is: java -classpath <classpath> molmed.apps.SthlmClinical2UUSNP <meta file> <new root dir>"

  val metaFile = try {
    new File(args(0))
  } catch {
    case e: ArrayIndexOutOfBoundsException =>
      throw new IllegalArgumentException(usage)
  }

  val rootDir = try {
    new File(args(1))
  } catch {
    case e: ArrayIndexOutOfBoundsException =>
      throw new IllegalArgumentException(usage)
  }

  rootDir.mkdirs()
  val reportFile = new File(rootDir + "/report.tsv")
  val reportWriter = new PrintWriter(reportFile)

  reportWriter.println(List("#SampleName", "Lane", "ReadLibrary", "FlowcellId").mkString("\t"))

  private val lines = Source.fromFile(metaFile).getLines.map(line => {
    val elements = line.split("\\s+")
    elements
  })

  for (line <- lines) {
    val sampleName = line(0)
    val flowCellId = line(1)
    val lane = line(2).toInt
    val index = line(3)

    val firstInPair = line(4)
    val secondInPair = line(5)

    // Sometime the meta data file contains more files than the folder...
    if (new File(firstInPair).exists() && new File(secondInPair).exists()) {
      // Create Sample folders    
      val uuSampleFolder = new File(rootDir + "/Sample_" + sampleName + "/")
      uuSampleFolder.mkdirs()

      // Create link hard links for the files
      def createHardLink(readPair: String, readPairNumber: Int) = {
        val uuStyleFileName = List(
          sampleName,
          index,
          "L" + GeneralUtils.getZerroPaddedIntAsString(lane, 3),
          "R" + readPairNumber,
          "001.fastq.gz").mkString("_")

        val targetFile = new File(uuSampleFolder + "/" + uuStyleFileName)
        Files.createLink(Paths.get(targetFile.getAbsolutePath()), Paths.get(readPair))
      }

      createHardLink(firstInPair, 1)
      createHardLink(secondInPair, 2)

      reportWriter.println(List(sampleName, lane, sampleName, flowCellId).mkString("\t"))
    }
  }

  reportWriter.close()

}