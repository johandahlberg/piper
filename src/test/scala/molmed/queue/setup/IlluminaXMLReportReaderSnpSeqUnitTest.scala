package molmed.queue.setup

import org.testng.annotations._
import org.testng.Assert
import java.io.File
import molmed.queue.SnpSeqBaseTest

class IlluminaXMLReportReaderSnpSeqUnitTest {

  /*
     * Note the these tests are dependent on the report.xml file, so if that is changed the tests need to be updated.
     */
  val baseTest = SnpSeqBaseTest
  val reportFile: File = new File(baseTest.pathToReportXML)
  val illuminaXMLReportReader = new IlluminaXMLReportReader(reportFile)
  val sampleName = "1"
  val lane = 1
  val flowcellId = "C0HNDACXX"

  @Test
  def testGetLanes {
    val expected: List[Int] = List(1)
    val actual: List[Int] = illuminaXMLReportReader.getLanes(sampleName)
    assert(actual.equals(expected))
  }

  @Test
  def testGetLanesMoreThanOneLaneForSample {
    val expected: List[Int] = List(1, 2)

    val illuminaXMLReportReaderForFileWithMoreThanOneLanePerSample =
      new IlluminaXMLReportReader(new File(baseTest.pathToReportXMLForSameSampleAcrossMultipleLanes))

    val actual: List[Int] = illuminaXMLReportReaderForFileWithMoreThanOneLanePerSample.getLanes(sampleName)

    assert(actual.equals(expected))
  }

  @Test
  def testGetReadLibrary() {
    val expected: String = "CEP_C13-NA11992"
    val actual: String = illuminaXMLReportReader.getReadLibrary(sampleName, lane)
    assert(actual.equals(expected))
  }

  @Test
  def testGetReadLibraryWhenDifferentForDifferentLanes() {
    val expected1: String = "SX211_04_076.2"
    val expected2: String = "SX211_04_076.1"
      

    val reportFile: File = new File(baseTest.pathToReportWithMultipleLibraries)
    val illuminaXMLReportReader = new IlluminaXMLReportReader(reportFile)
    val sampleName = "04_076"        
    val lane5 = 5
    val lane6 = 6

    val actual1: String = illuminaXMLReportReader.getReadLibrary(sampleName, lane5)
    val actual2: String = illuminaXMLReportReader.getReadLibrary(sampleName, lane6)
    
    assert(actual1.equals(expected1), "actual=" + actual1 + " expected=" + expected1)
    assert(actual2.equals(expected2), "actual=" + actual2 + " expected=" + expected2)
  }

  @Test
  def testGetFlowcellId() {
    val expected: String = flowcellId
    val actual: String = illuminaXMLReportReader.getFlowcellId()
    assert(actual.equals(expected))
  }

  @Test
  def testGetPlatformUnitID() {
    val expected: String = flowcellId + "." + sampleName + "." + lane
    val actual: String = illuminaXMLReportReader.getPlatformUnitID(sampleName, lane)
    assert(actual.equals(expected))
  }

  @Test
  def testGetReadGroupID() {
    val expected: String = flowcellId + "." + sampleName + "." + lane
    val actual: String = illuminaXMLReportReader.getReadGroupID(sampleName, lane)
    assert(actual.equals(expected))
  }
}