package molmed.queue.setup

import org.testng.annotations._
import org.testng.Assert
import java.io.File
import molmed.queue.SnpSeqBaseTest

class FlatFileReportReaderSnpSeqUnitTest {

  /*
     * Note the these tests are dependent on the report.xml file, so if that is changed the tests need to be updated.
     */
  val baseTest = SnpSeqBaseTest
  val reportFile: File = new File(baseTest.pathToFlatFileReport)
  val flatFileReportReader = new FlatFileReportReader(reportFile)

  @Test
  def testGetLanes {
    val expected: List[Int] = List(1,2)
    val actual: List[Int] = flatFileReportReader.getLanes("MyFirstSample")
    assert(actual.equals(expected))
  }

  @Test
  def testGetReadLibrary() {
    val expected: String = "FirstLib"
    val actual: String = flatFileReportReader.getReadLibrary("MyFirstSample", 1)
    assert(actual.equals(expected))
  }

  @Test
  def testGetFlowcellId() {
    val expected: String = "9767892AVF"
    val actual: String = flatFileReportReader.getFlowcellId()
    assert(actual.equals(expected))
  }
}