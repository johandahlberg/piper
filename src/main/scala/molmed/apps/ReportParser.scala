package molmed.apps

import java.io.File
import molmed.queue.setup.SetupXMLReader
import java.io.PrintWriter

/**
 * A simple application to get some basic information from a setup file.
 * Right now it support getting:
 * 	the sample
 *  the number of libraries sequenced for the sample
 *  the number of reads passed filter from the sequencer for the sample
 *  
 * Usage is: java -classpath <classpath> molmed.apps.ReportParser <setup xml> <output file>
 */
object ReportParser extends App {

  val usage = "Missing input file. Usage is: java -classpath <classpath> molmed.apps.ReportParser <setup xml> <output file>"
  
  val setupFile = try {
    new File(args(0))
  } catch {
    case e: ArrayIndexOutOfBoundsException =>
      throw new IllegalArgumentException(usage)
  }

  val outputFile = try {
    new File(args(1))
  } catch {
    case e: ArrayIndexOutOfBoundsException =>
      throw new IllegalArgumentException(usage)
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