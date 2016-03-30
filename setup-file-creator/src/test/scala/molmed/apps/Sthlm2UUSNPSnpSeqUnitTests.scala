package molmed.apps

import org.testng.annotations._
import org.testng.Assert
import java.io.File
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
    library = "A",
    lane = 1,
    date = "140528",
    flowCellId = "BC423WACXX",
    index = "1",
    fastq = new File(sthlmRootDir +
      "/P1171_102/A/140702_AC41A2ANXX/" +
      "P1171_102_ATTCAGAA-CCTATCCT_L001_R1_001.fastq.gz"),
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
        "sthlm_runfolder_root/"
    val expected = List(
      new File(root + "P1171_102/A/140702_AC41A2ANXX/P1171_102_ATTCAGAA-CCTATCCT_L001_R1_001.fastq.gz"),
      new File(root + "P1171_102/A/140702_AC41A2ANXX/P1171_102_ATTCAGAA-CCTATCCT_L001_R2_001.fastq.gz"),
      new File(root + "P1171_102/A/140702_AC41A2ANXX/P1171_102_ATTCAGAA-CCTATCCT_L002_R2_001.fastq.gz"),
      new File(root + "P1171_102/A/140702_AC41A2ANXX/P1171_102_ATTCAGAA-CCTATCCT_L002_R1_001.fastq.gz"),
      new File(root + "P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L002_R2_001.fastq.gz"),
      new File(root + "P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L002_R1_001.fastq.gz"),
      new File(root + "P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L001_R2_001.fastq.gz"),
      new File(root + "P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L001_R1_001.fastq.gz"))

    val result = Sthlm2UUSNP.getFastqFiles(sthlmRootDir).toList

    assert(expected.sorted == result.sorted, "Did not find correct fastq files: Found: " + result)

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
        "P1142_101	1	A	BC423WACXX"

    val result = Sthlm2UUSNP.addToReport(Seq(testSampleInfo), uuRoot)

    assert(result.exists(), "Did not create report")
    assert(result.getName() == "report.tsv", "Did not create report.tsv")
    assert(result.getParentFile() == uuRoot)

    val fileContent = Source.fromFile(result).getLines().mkString("\n")

    assert(fileContent == expected,
      "fileContent=\n" + fileContent)
  }

  val expectedSampleInfoMap =
    Map(
      new File("test_tmp/test_uu_sample_folder/140702_AC41A2ANXX") ->
        List(
          new Sthlm2UUSNP.SampleInfo("P1171_102", "A", 1, "140702", "AC41A2ANXX", "ATTCAGAA-CCTATCCT", new File("src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_102/A/140702_AC41A2ANXX/P1171_102_ATTCAGAA-CCTATCCT_L001_R1_001.fastq.gz"), 1),
          new Sthlm2UUSNP.SampleInfo("P1171_102", "A", 1, "140702", "AC41A2ANXX", "ATTCAGAA-CCTATCCT", new File("src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_102/A/140702_AC41A2ANXX/P1171_102_ATTCAGAA-CCTATCCT_L001_R2_001.fastq.gz"), 2),
          new Sthlm2UUSNP.SampleInfo("P1171_102", "A", 2, "140702", "AC41A2ANXX", "ATTCAGAA-CCTATCCT", new File("src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_102/A/140702_AC41A2ANXX/P1171_102_ATTCAGAA-CCTATCCT_L002_R1_001.fastq.gz"), 1),
          new Sthlm2UUSNP.SampleInfo("P1171_102", "A", 2, "140702", "AC41A2ANXX", "ATTCAGAA-CCTATCCT", new File("src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_102/A/140702_AC41A2ANXX/P1171_102_ATTCAGAA-CCTATCCT_L002_R2_001.fastq.gz"), 2),

          new Sthlm2UUSNP.SampleInfo("P1171_104", "A", 1, "140702", "AC41A2ANXX", "ATTCAGAA-GGCTCTGA", new File("src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L001_R1_001.fastq.gz"), 1),
          new Sthlm2UUSNP.SampleInfo("P1171_104", "A", 1, "140702", "AC41A2ANXX", "ATTCAGAA-GGCTCTGA", new File("src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L001_R2_001.fastq.gz"), 2),
          new Sthlm2UUSNP.SampleInfo("P1171_104", "A", 2, "140702", "AC41A2ANXX", "ATTCAGAA-GGCTCTGA", new File("src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L002_R1_001.fastq.gz"), 1),
          new Sthlm2UUSNP.SampleInfo("P1171_104", "A", 2, "140702", "AC41A2ANXX", "ATTCAGAA-GGCTCTGA", new File("src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L002_R2_001.fastq.gz"), 2)))

  @AfterMethod
  def afterGenerateFileStructureTest {
    TestPaths.tearDownTestFolders()
  }

  @Test
  def generateFileStructureTest {

    import TestPaths._
    createTestFolders()

    val config = new Sthlm2UUSNP.Config(Some(sthlmRootDir), Some(uuRoot), Seq())
    val actual = Sthlm2UUSNP.generateFileStructure(config)

    assert(
      expectedSampleInfoMap.map(x => x._2.sortBy(f => f.sampleName))
        == actual.map(x => x._2.sortBy(f => f.sampleName)))

  }

  @AfterMethod
  def afterGenerateFileStructureAndOnlyIncludeSomeRunfolderTest {
    TestPaths.tearDownTestFolders()
  }

  @Test
  def generateFileStructureAndOnlyIncludeSomeRunfolderTest {

    import TestPaths._
    createTestFolders()

    // Note the runfolder is set
    val config = new Sthlm2UUSNP.Config(Some(sthlmRootDir), Some(uuRoot), Seq("140702_AC41A2ANXX"))
    val actual = Sthlm2UUSNP.generateFileStructure(config)

    assert(
      expectedSampleInfoMap.map(x => x._2.sortBy(f => f.sampleName))
        == actual.map(x => x._2.sortBy(f => f.sampleName)))

  }

  @AfterMethod
  def afterGenerateFileStructureAndDoNotIncludeRunfolderTest {
    TestPaths.tearDownTestFolders()
  }

  @Test
  def generateFileStructureAndDoNotIncludeRunfolderTest {

    import TestPaths._
    createTestFolders()

    // Note the runfolder is set to something else than the runfolder available
    // under sthlmRootDir
    val config = new Sthlm2UUSNP.Config(Some(sthlmRootDir), Some(uuRoot), Seq("141202_AC41A2ANXX"))
    val actual = Sthlm2UUSNP.generateFileStructure(config)

    assert(Map() == actual)

  }

  @Test
  def listSubDirectoriesTest {

    val expected = Seq(
      new File(
        "src/test/resources/testdata/Sthlm2UUTests/" +
          "sthlm_runfolder_root/P1171_102"),
      new File(
        "src/test/resources/testdata/Sthlm2UUTests/" +
          "sthlm_runfolder_root/P1171_104"))

    val actual = Sthlm2UUSNP.listSubDirectories(sthlmRootDir)

    assert(actual.sorted == expected.sorted, "acutal=" + actual + " expected=" + expected)
  }

  @Test
  def parseSampleInfoFromFileHierarchyTest {

    val libraryfolder = new File("A")
    val runfolder = new File("140528_BC423WACXX")
    val fileToParse = new File("P1142_101_NoIndex_L001_R1_001.fastq.gz")

    val expeced = new Sthlm2UUSNP.SampleInfo(
      sampleName = "P1142_101",
      library = "A",
      lane = 1,
      date = "140528",
      flowCellId = "BC423WACXX",
      index = "NoIndex",
      fastq = fileToParse,
      read = 1)

    val actual = Sthlm2UUSNP.
      parseSampleInfoFromFileHierarchy(fileToParse, runfolder, libraryfolder)

    assert(actual == expeced)
  }

  @Test
  def parseSampleInfoFromFileHierarchyWithDualIndexing {
    val libraryfolder = new File("A")
    val runfolder = new File("140528_BC423WACXX")
    val fileToParse =
      new File("P1171_102_ATTCAGAA-CCTATCCT_L001_R1_001.fastq.gz")

    val expeced = new Sthlm2UUSNP.SampleInfo(
      sampleName = "P1171_102",
      library = "A",
      lane = 1,
      date = "140528",
      flowCellId = "BC423WACXX",
      index = "ATTCAGAA-CCTATCCT",
      fastq = fileToParse,
      read = 1)

    val actual = Sthlm2UUSNP.
      parseSampleInfoFromFileHierarchy(fileToParse, runfolder, libraryfolder)

    assert(actual == expeced)
  }

}