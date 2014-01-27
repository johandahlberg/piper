package molmed.apps

import java.io.File
import scala.io.Source
import molmed.utils.GeneralUtils
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Utility program to convert sthlm sequencing platform meta format to UU SNP format.
 */
object Sthml2UUSNP extends App {

  val usage = "Missing input file. Usage is: java -classpath <classpath> molmed.apps.Sthml2UUSNP <old root> <new root dir>"

  val sthlmRoot = try {
    new File(args(0))
  } catch {
    case e: ArrayIndexOutOfBoundsException =>
      throw new IllegalArgumentException(usage)
  }

  val newRootDir = try {
    new File(args(1))
  } catch {
    case e: ArrayIndexOutOfBoundsException =>
      throw new IllegalArgumentException(usage)
  }

  val reportFile = new File(newRootDir + "/report.tsv")
  val reportWriter = new PrintWriter(reportFile)

  reportWriter.println(List("#SampleName", "Lane", "ReadLibrary", "FlowcellId").mkString("\t"))

  val files = GeneralUtils.getFileTree(sthlmRoot).filter(p => p.getName().contains(".fastq.gz"))

  for (file <- files) {

    // 1_131129_AH7W5YADXX_P700_401_2.fastq.gz
    val fileName = file.getName()

    val splitFileName = fileName.split("_")

    val lane = splitFileName(0).toInt
    val flowCellId = splitFileName(2)
    val indexOfLastPart = splitFileName.indexWhere(s => s.contains(".fastq.gz"))
    val sampleName = splitFileName.slice(3, indexOfLastPart).mkString("_")
    // TODO Ugly hack since we don't know the index used
    val index = "AAAAAA"

    // Create Sample folders    
    val uuSampleFolder = new File(newRootDir + "/Sample_" + sampleName + "/")
    uuSampleFolder.mkdirs()

    // Create link hard links for the files
    def createHardLink(readPair: File, readPairNumber: Int) = {
      val uuStyleFileName = List(
        sampleName,
        index,
        GeneralUtils.getZerroPaddedIntAsString(lane, 3),
        "R" + readPairNumber,
        "001.fastq.gz").mkString("_")

      val targetFile = new File(uuSampleFolder + "/" + uuStyleFileName)
      Files.createLink(Paths.get(targetFile.getAbsolutePath()), Paths.get(readPair.getAbsolutePath()))
    }

    if(splitFileName(indexOfLastPart).contains("1")) createHardLink(file, 1) else createHardLink(file, 2)     

    reportWriter.println(List(sampleName, lane, sampleName, flowCellId).mkString("\t"))

  }
  reportWriter.close()
}