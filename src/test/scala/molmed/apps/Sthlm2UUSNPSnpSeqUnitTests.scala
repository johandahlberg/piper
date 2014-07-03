package molmed.apps

import org.testng.annotations._
import org.testng.Assert
import java.io.File
import molmed.queue.SnpSeqBaseTest
import scala.reflect.io.Directory

class Sthlm2UUSNPSnpSeqUnitTests {

  object TestPaths {
    val testOutputPath: File = new File("test_tmp")
    val testTmpFolder: Directory = new Directory(testOutputPath)
    val uuRoot: File = new File(testOutputPath + "/test_uu_sample_folder")

    def createTestFolders(): Unit = {
      testOutputPath.mkdirs()
      uuRoot.mkdirs()
    }

    def tearDownTestFolders(): Unit = {
      testTmpFolder.deleteRecursively
    }
  }

  //  val outputFolder = new Directory(uuRoot)

  //  @AfterClass
  //  def removeAllTmpFiles: Unit = {
  //    testTmpFolder.deleteRecursively
  //  }

  val sthlmRootDir = new File(
    "src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root")

  val testSampleInfo = new Sthlm2UUSNP.SampleInfo(
    sampleName = "P1142_101",
    lane = 1,
    flowCellId = "BC423WACXX",
    index = "1",
    fastq = new File(sthlmRootDir +
      "/P1142_101/140528_BC423WACXX/1_140528_BC423WACXX_P1142_101_1.fastq.gz"),
    read = 1)

  @Test
  def createHardLinkTest {

    import TestPaths._

    createTestFolders()

    val result = Sthlm2UUSNP.createHardLink(
      testSampleInfo,
      uuRoot)

    assert(result.exists(), "Did not create file: " + result)
    assert(result.getParentFile() == uuRoot, "Did not create file: " + result)

    tearDownTestFolders()

  }

  @Test
  def getFastqFilesTest {
    val root =
      "src/test/resources/testdata/Sthlm2UUTests/" +
        "sthlm_runfolder_root/P1142_101/"
    val expected = List(
      new File(root + "140528_BC423WACXX/1_140528_BC423WACXX_P1142_101_1.fastq.gz"),
      new File(root + "140528_BC423WACXX/1_140528_BC423WACXX_P1142_101_2.fastq.gz"),
      new File(root + "140528_BC423WACXX/2_140528_BC423WACXX_P1142_101_1.fastq.gz"),
      new File(root + "140528_BC423WACXX/2_140528_BC423WACXX_P1142_101_2.fastq.gz"))

    val result = Sthlm2UUSNP.getFastqFiles(sthlmRootDir).toList

    assert(expected == result, "Did not find correct fastq files")

  }

  @Test
  def hardlinkAndAddToReportTest {
    import TestPaths._

    createTestFolders()

    val result = Sthlm2UUSNP.hardlinkAndAddToReport(testSampleInfo, uuRoot)

    assert(result._1.exists(),
      "Did not create (hardlink) file: " + result._1)

    assert(result._2.exists(), "Did not create report")
    assert(result._2.getName() == "report.tsv", "Did not create report.tsv")
    //@TODO Might add test of report.tsv content being correct later...        

    tearDownTestFolders()
  }

  @Test
  def listSubDirectoriesTest {

    val expected = Seq(
      new File(
        "src/test/resources/testdata/Sthlm2UUTests/" +
          "sthlm_runfolder_root/P1142_101"))

    val actual = Sthlm2UUSNP.listSubDirectories(sthlmRootDir)

    assert(actual == expected, "acutal=" + actual + " expected=" + expected)
  }

  @Test
  def parseSampleInfoFromFileNameTest {

    val fileToParse = new File("1_140528_BC423WACXX_P1142_101_1.fastq.gz")

    val expeced = new Sthlm2UUSNP.SampleInfo(
      sampleName = "P1142_101",
      lane = 1,
      flowCellId = "BC423WACXX",
      index = "AAAAAA",
      fastq = fileToParse,
      read = 1)

    val actual = Sthlm2UUSNP.parseSampleInfoFromFileName(fileToParse)

    assert(actual == expeced)
  }

}