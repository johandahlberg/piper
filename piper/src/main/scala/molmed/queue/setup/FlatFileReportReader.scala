package molmed.queue.setup

import scala.collection.immutable.Map
import java.io.File
import org.apache.commons.lang.NotImplementedException
import scala.io.Source

/**
 * A reader for the flat file alternative to the xml setup file which is covered
 * by the IlluminaXMLReportReader.
 *
 * This should be able to read a tab separated file on the following format:
 * #SampleName Lane    ReadLibrary FlowcellId
 * MyFirstSample   1   FirstLib    9767892AVF
 * MyFirstSample   2   SecondLib   9767892AVF
 * MySecondSample  1   SomeOtherLib    9767892AVF
 * 
 * @see molmed.queue.setup.ReportReaderAPI
 */
class FlatFileReportReader(setupFile: File) extends ReportReaderAPI {

  case class FlatFileSetupLine(sampleName: String, lane: Int, library: String, flowCellId: String)

  private val lines = Source.fromFile(setupFile).getLines.filter(s => !s.startsWith("#")).map(line => {
    val elements = line.split("\\s+")
    new FlatFileSetupLine(elements(0), elements(1).toInt, elements(2), elements(3))
  }).toList

  def getReadLibrary(sampleName: String, lane: Int): String = {

    val matches = lines.filter(s =>
      s.sampleName.equals(sampleName) &&
        s.lane == lane)

    assert(!(matches.size > 1), "Found more than one match for sample: " + sampleName + " lane: " + lane + ". This should only match once." +
      "matches=" + matches)
    assert(!matches.isEmpty, "Didn't find match for sample: " + sampleName + " lane: " + lane)

    matches(0).library
  }

  def getFlowcellId(): String = {
    lines(0).flowCellId
  }

  def getLanes(sampleName: String): List[Int] = {
    lines.
      filter(s => s.sampleName.equals(sampleName)).
      map(s => s.lane)
  }

  def getNumberOfReadsPassedFilter(sampleName: String, lane: Int): Option[Int] = None
}