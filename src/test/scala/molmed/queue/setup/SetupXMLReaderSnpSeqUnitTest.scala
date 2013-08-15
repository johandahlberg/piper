package molmed.queue.setup

import org.testng.annotations._
import org.testng.Assert
import collection.JavaConversions._
import java.io.File
import molmed.queue.SnpSeqBaseTest
import scala.collection.Seq
import molmed.queue.setup.stubs.IlluminaXMLReportReaderStub
import java.io.PrintWriter

class SetupXMLReaderSnpSeqUnitTest {

    /*
     * Note the these tests are dependent on the report.xml file, so if that is changed the tests need to be updated.
     */
    val baseTest = SnpSeqBaseTest
    val setupFile: File = new File(baseTest.pathSetupFile)
    val setupXMLReader = new SetupXMLReader(setupFile)
    val sampleName = "1"
    val runFolderName = "src/test/resources/testdata/smallTestFastqDataFolder/report.xml"

    @BeforeMethod
    def beforeTest() {
        val baseTest = SnpSeqBaseTest
        val setupFile: File = new File("src/test/resources/testdata/exampleForNewSetupXML.xml")
        val setupXMLReader = new SetupXMLReader(setupFile)
        val sampleName = "1"
    }

    @AfterMethod
    def afterTest() {
        val baseTest = null
        val setupFile: File = null
        val setupXMLReader = null
        val sampleName = null
    }

    @Test
    def TestGetPlatform() = {
        val expected: String = "Illumina"
        val actual: String = setupXMLReader.getPlatform()
        assert(actual.equals(expected))
    }

    @Test
    def TestGetSequencingCenter() = {
        val expected: String = "SNP_SEQ_PLATFORM"
        val actual: String = setupXMLReader.getSequencingCenter()
        assert(actual.equals(expected))
    }

    @Test
    def TestGetProjectName() = {
        val expected: String = "TestProject"
        val actual: String = setupXMLReader.getProjectName()
        assert(actual.equals(expected))
    }

    @Test
    def TestGetSamples() = {

        val illuminaXMLReportReader: IlluminaXMLReportReaderAPI = new IlluminaXMLReportReaderStub()

        val actual = setupXMLReader.getSamples()

        val expected = Map(
            "1" -> List(
                Sample("1", new File("src/test/resources/testdata/exampleFASTA.fasta"),
                    ReadGroupInformation("1", "C0HNDACXX.1.1", "SNP_SEQ_PLATFORM", "CEP_C13-NA11992", "Illumina", "C0HNDACXX.1.1"),
                    ReadPairContainer(new File("src/test/resources/testdata/smallTestFastqDataFolder/Sample_1/exampleFASTQ_L001_R1_file.fastq").getAbsoluteFile(),
                        new File("src/test/resources/testdata/smallTestFastqDataFolder/Sample_1/exampleFASTQ_L001_R2_file.fastq").getAbsoluteFile(), "1"))))           
        assert(expected.sameElements(expected))
        assert(expected.keys.equals(actual.keys))
    }

    @Test
    def TestGetSameSampleFromSeveralLanesInSameRunFolder() = {

        // Reset some of the shared resources
        val setupFile: File = new File("src/test/resources/testdata/newPipelineSetupSameSampleAcrossMultipleLanes.xml")
        val setupXMLReader = new SetupXMLReader(setupFile)

        val expected: Map[String, Seq[molmed.queue.setup.SampleAPI]] = Map(
            "1" -> List(
                Sample("1", new File("src/test/resources/testdata/exampleFASTA.fasta").getAbsoluteFile(),
                    ReadGroupInformation("1", "C0HNDACXX.1.1", "SNP_SEQ_PLATFORM", "CEP_C13-NA11992", "Illumina", "C0HNDACXX.1.1"),
                    ReadPairContainer(new File("src/test/resources/testdata/runFolderWithSameSampleInMultipleLanes/Sample_1/exampleFASTQ_L001_R1_file.fastq").getAbsoluteFile(),
                        new File("src/test/resources/testdata/runFolderWithSameSampleInMultipleLanes/Sample_1/exampleFASTQ_L001_R2_file.fastq").getAbsoluteFile(),
                        "1")),
                Sample("1", new File("src/test/resources/testdata/exampleFASTA.fasta").getAbsoluteFile(),
                    ReadGroupInformation("1", "C0HNDACXX.1.2", "SNP_SEQ_PLATFORM", "CEP_C13-NA11992", "Illumina", "C0HNDACXX.1.2"),
                    ReadPairContainer(new File("src/test/resources/testdata/runFolderWithSameSampleInMultipleLanes/Sample_1/exampleFASTQ_L002_R1_file.fastq").getAbsoluteFile(),
                        new File("src/test/resources/testdata/runFolderWithSameSampleInMultipleLanes/Sample_1/exampleFASTQ_L002_R2_file.fastq").getAbsoluteFile(),
                        "1"))))

        // Run the test and evaluate the result
        val actual: Map[String, Seq[SampleAPI]] = setupXMLReader.getSamples()
        assert(expected.sameElements(actual))
        assert(expected.keys.equals(actual.keys))
    }

    @Test
    def TestGetSameSampleFromSeveralRunFolders() = {

        // Reset some of the shared resources
        val setupFile: File = new File("src/test/resources/testdata/newPipelineSetupSameSampleAcrossMultipleRunFolders.xml")
        val setupXMLReader = new SetupXMLReader(setupFile)

        val expected: Map[String, Seq[molmed.queue.setup.SampleAPI]] = Map(
            "1" -> List(
                Sample("1", new File("src/test/resources/testdata/exampleFASTA.fasta").getAbsoluteFile(),
                    ReadGroupInformation("1", "C0HNDACXX.1.1", "SNP_SEQ_PLATFORM", "CEP_C13-NA11992", "Illumina", "C0HNDACXX.1.1"),
                    ReadPairContainer(new File("src/test/resources/testdata/runFoldersForMultipleSample/runfolder1/Sample_1/exampleFASTQ_L001_R1_file.fastq").getAbsoluteFile(),
                        new File("src/test/resources/testdata/runFoldersForMultipleSample/runfolder1/Sample_1/exampleFASTQ_L001_R2_file.fastq").getAbsoluteFile(),
                        "1")),
                Sample("1", new File("src/test/resources/testdata/exampleFASTA.fasta").getAbsoluteFile(),
                    ReadGroupInformation("1", "AAAAAAAAA.1.1", "SNP_SEQ_PLATFORM", "SomeOtherLibraryName", "Illumina", "AAAAAAAAA.1.1"),
                    ReadPairContainer(new File("src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/Sample_1/exampleFASTQ_L001_R1_file.fastq").getAbsoluteFile(),
                        new File("src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/Sample_1/exampleFASTQ_L001_R2_file.fastq").getAbsoluteFile(),
                        "1"))))

        // Run the test and evaluate the result
        val actual: Map[String, Seq[SampleAPI]] = setupXMLReader.getSamples()

        assert(expected.sameElements(actual))
        assert(expected.keys.equals(actual.keys))        
    }

    @Test
    def TestGetReference() = {
        val expected: File = new File("src/test/resources/testdata/exampleFASTA.fasta").getAbsoluteFile()
        val actual: File = setupXMLReader.getReference(sampleName)
        assert(expected == actual)
    }

    @Test
    def TestGetUppmaxProjectId() = {
        val expected: String = "a2009002"
        val actual: String = setupXMLReader.getUppmaxProjectId()
        assert(actual.equals(expected))

    }

}