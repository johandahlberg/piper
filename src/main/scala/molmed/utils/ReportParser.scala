package molmed.utils

import java.io.File
import molmed.queue.setup.SetupXMLReader
import java.io.PrintWriter

object ReportParser extends App {

  val setupFile = try {
    new File(args(0))
  } catch {
    case e: ArrayIndexOutOfBoundsException =>
      throw new IllegalArgumentException("Missing input file. Usage is: java -classpath <classpath> molmed.queue.utils.ReportParser <setup xml> <output file>")
  }

  val outputFile = try {
    new File(args(1))
  } catch {
    case e: ArrayIndexOutOfBoundsException =>
      throw new IllegalArgumentException("Missing output file. Usage is: java -classpath <classpath> molmed.queue.utils.ReportParser <setup xml> <output file>")
  }

  val setupXMLReader = new SetupXMLReader(setupFile)
  val printWriter = new PrintWriter(outputFile)
  
  
  // Write file header
  printWriter.println(List("sample", "nbrOfLibraries", "readPairsPassFilter").mkString("\t"))
  
  val sampleNameAndSamples = setupXMLReader.getSamples()

  for (sampleName <- sampleNameAndSamples.keys) {
    val samples = sampleNameAndSamples(sampleName)
    val nbrOfLibraries = samples.map(x => x.getReadGroupInformation.readLibrary).toSet.size
    val readsPassFilter = samples.map(x => x.getReadGroupInformation.readsPassFilter.get / 2).reduce(_ + _)

    // Write rows
    printWriter.println(List(sampleName, nbrOfLibraries, readsPassFilter).mkString("\t"))

  }

  printWriter.close()
  
}