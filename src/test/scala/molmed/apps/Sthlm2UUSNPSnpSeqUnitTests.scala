package molmed.apps

import org.testng.annotations._
import org.testng.Assert
import java.io.File
import molmed.queue.SnpSeqBaseTest
import scala.reflect.io.Directory
import scala.io.Source

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

  val sthlmRootDir = new File(
    "src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root")

  val testSampleInfo = new Sthlm2UUSNP.SampleInfo(
    sampleName = "P1142_101",
    lane = 1,
    date = "140528",
    flowCellId = "BC423WACXX",
    index = "1",
    fastq = new File(sthlmRootDir +
      "/P1142_101/140528_BC423WACXX/1_140528_BC423WACXX_P1142_101_1.fastq.gz"),
    read = 1)

  @AfterMethod
  def afterCreateHardLinkTest {
    TestPaths.tearDownTestFolders()
  }
  @Test
  def createHardLinkTest {

    import TestPaths._

    createTestFolders()

    val runFolder =
      new File(
        uuRoot + "/" + testSampleInfo.date + "_" + testSampleInfo.flowCellId)

    val result = Sthlm2UUSNP.createHardLink(
      testSampleInfo,
      runFolder)

    assert(result.exists(), "Did not create file: " + result)

    assert(result.getParentFile().getName() ==
      "Sample_" + testSampleInfo.sampleName,
      "Did not create sample dir.")

    assert(result.getParentFile().getParentFile().getName() ==
      testSampleInfo.date + "_" + testSampleInfo.flowCellId,
      "Did create correct folder structure: " + result)
  }

  @AfterMethod
  def afterCreateHardLinkTwiceTest {
    TestPaths.tearDownTestFolders()
  }
  @Test
  def createHardLinkTwiceTest {

    import TestPaths._

    createTestFolders()

    val runFolder =
      new File(
        uuRoot + "/" + testSampleInfo.date + "_" + testSampleInfo.flowCellId)

    Sthlm2UUSNP.createHardLink(
      testSampleInfo,
      runFolder)

    val result =
      Sthlm2UUSNP.createHardLink(
        testSampleInfo,
        runFolder)

    assert(result.exists(), "Did not create file: " + result)

    assert(result.getParentFile().getName() ==
      "Sample_" + testSampleInfo.sampleName,
      "Did not create sample dir.")

    assert(result.getParentFile().getParentFile().getName() ==
      testSampleInfo.date + "_" + testSampleInfo.flowCellId,
      "Did create correct folder structure: " + result)
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

  @AfterMethod
  def afterAddToReportTest {
    TestPaths.tearDownTestFolders()
  }
  @Test
  def addToReportTest {

    import TestPaths._
    createTestFolders()
    
    val expected =
      "#SampleName	Lane	ReadLibrary	FlowcellId\n" +
        "P1142_101	1	P1142_101	BC423WACXX"

    val result = Sthlm2UUSNP.addToReport(Seq(testSampleInfo), uuRoot)

    assert(result.exists(), "Did not create report")
    assert(result.getName() == "report.tsv", "Did not create report.tsv")
    assert(result.getParentFile() == uuRoot)

    val fileContent = Source.fromFile(result).getLines().mkString("\n")

    assert(fileContent == expected,
      "fileContent=\n" + fileContent)
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

    val runfolder = new File("140528_BC423WACXX")
    val fileToParse = new File("P1142_101_NoIndex_L001_R1_001.fastq.gz")

    val expeced = new Sthlm2UUSNP.SampleInfo(
      sampleName = "P1142_101",
      lane = 1,
      date = "140528",
      flowCellId = "BC423WACXX",
      index = "NoIndex",
      fastq = fileToParse,
      read = 1)

    val actual = Sthlm2UUSNP.
      parseSampleInfoFromFileNameAndRunfolder(fileToParse, runfolder)

    assert(actual == expeced)
  }

}