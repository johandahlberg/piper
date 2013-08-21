package molmed.queue.setup

import java.io.File
import molmed.xml.illuminareport.SequencingReport
import java.io.StringReader
import javax.xml.bind.JAXBContext
import scala.collection.mutable.Buffer
import molmed.xml.illuminareport.Read
import javax.xml.bind.Marshaller
import collection.JavaConversions._

class IlluminaXMLReportReader(file: File) extends IlluminaXMLReportReaderAPI {

  /**
   * XML related fields
   */
  val context = JAXBContext.newInstance(classOf[SequencingReport])
  val illuminaUnmarshaller = context.createUnmarshaller()
  val illuminaReportreader = new StringReader(scala.io.Source.fromFile(file).mkString)
  val illuminaProject = illuminaUnmarshaller.unmarshal(illuminaReportreader).asInstanceOf[SequencingReport]

  /**
   * Keeping the sample list as a field for convenience
   */
  private val sampleList = illuminaProject.getSampleMetrics().flatMap(sampleMetric => sampleMetric.getSample())

  private def getMatchingSamples(sampleName: String): Buffer[molmed.xml.illuminareport.Sample] =
    sampleList.filter(p => p.getId().equalsIgnoreCase(sampleName))

  private def getReadForSamples(samples: Buffer[molmed.xml.illuminareport.Sample]): Buffer[Read] = {
    samples.flatMap(f =>
      f.getTag().flatMap(p =>
        p.getLane().flatMap(x =>
          x.getRead())))
  }

  def getReadLibrary(sampleName: String, lane: Int): String = {

    // Get all matching samples
    val matchingSamples = getMatchingSamples(sampleName)

    // Get the one with the correct lane
    val sampleForLane = for (
      sample <- matchingSamples;
      tag <- sample.getTag();
      lanes <- tag.getLane();
      if lanes.getId() == lane        
    ) yield {
      sample
    }

    require(sampleForLane.size == 1, "sample: " + sampleName + " sequenced more than once in the same lane. Right now this is not supported." +
      "Sample was: " + sampleForLane.map(f => println(f)) + "Size was: " + sampleForLane.size)

    // Get's library id
    val libraryName = getReadForSamples(sampleForLane).map(f => f.getLibraryName()).get(0)

    // Catch the cases where no library id has been given (for example in MiSeq runs)
    if (libraryName.isEmpty())
      sampleName + ".1"
    else
      libraryName

  }

  def getFlowcellId(): String =
    illuminaProject.getMetaData().getFlowCellId()

  def getPlatformUnitID(sampleName: String, lane: Int): String =
    getFlowcellId + "." + sampleName + "." + lane

  def getReadGroupID(sampleName: String, lane: Int): String = {
    getPlatformUnitID(sampleName, lane)
  }

  def getLanes(sampleName: String): List[Int] = {
    getMatchingSamples(sampleName).flatMap(f =>
      f.getTag().flatMap(p =>
        p.getLane().map(x =>
          x.getId().toInt))).toList
  }
}