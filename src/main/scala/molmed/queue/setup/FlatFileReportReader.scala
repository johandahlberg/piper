package molmed.queue.setup

import scala.collection.immutable.Map
import java.io.File
import org.apache.commons.lang.NotImplementedException
import scala.io.Source

class FlatFileReportReader(setupFile: File) extends ReportReaderAPI {

  case class FlatFileSetupLine(sampleName: String, lane: Int, library: String, flowCellId: String)

  val lines = Source.fromFile(setupFile).getLines.filter(s => !s.startsWith("#")).map(line => {
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
}