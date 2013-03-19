package molmed.queue.setup

import org.testng.annotations._
import org.testng.Assert
import molmed.queue.SnpSeqBaseTest
import molmed.queue.setup.stubs._
import java.io.File
import java.io.FileNotFoundException

class SampleSnpSeqUnitTest {

    val baseTest = new SnpSeqBaseTest()

    var sampleName = "TestSample"
    var setupXMLReader = new SetupXMLReaderStub()
    var illuminaXMLReportReader = new IlluminaXMLReportReaderStub()
    var runFolderName = "public/testdata/smallTestFastqDataFolder/report.xml" //TODO check that this is correct

    @BeforeMethod
    def beforeTest() {

        //Setup
        setupXMLReader = new SetupXMLReaderStub()
        setupXMLReader.sampleFolder = new File(baseTest.pathToSampleFolder)
        illuminaXMLReportReader = new IlluminaXMLReportReaderStub()

    }

    @AfterMethod
    def afterTest() {
        setupXMLReader = null
        illuminaXMLReportReader = null
    }

    @Test
    def testGetFastqs() {

        // Class under test
        val sample = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Run the test
        val expected = new ReadPairContainer(new File(baseTest.pathToMate1).getAbsoluteFile(), new File(baseTest.pathToMate2).getAbsoluteFile(), sampleName)
        val actual = sample.getFastqs()

        assert(actual == expected)
    }

    @Test(expectedExceptions = Array(classOf[FileNotFoundException]))
    def testGetFastqsThrowsFileNotFoundException() {

        // This will cause the exception
        setupXMLReader.sampleFolder = new File(baseTest.pathToBaseDir)

        // Class under test
        val sample = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Run the test
        val actual = sample.getFastqs()
    }

    @Test
    def testGetBwaReadGroupInformationString() {

        // Setup
        illuminaXMLReportReader.readGroupId = "ReadGroupId"
        setupXMLReader.sequencingCenter = "SequencingCenter"
        illuminaXMLReportReader.readLibrary = "ReadLibary"
        setupXMLReader.platform = "Platform"
        illuminaXMLReportReader.platformUnitId = "1"

        val expected: String = "\"" + """@RG\tID:""" + illuminaXMLReportReader.readGroupId + """\\tSM:""" + sampleName +
            """\\tCN:""" + setupXMLReader.sequencingCenter + """\\tLB:""" + illuminaXMLReportReader.readLibrary +
            """\\tPL:""" + setupXMLReader.platform + """\\tPU:""" + illuminaXMLReportReader.platformUnitId + "\""

        // Class under test
        val sample = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Run the test
        val actual: String = sample.getBwaStyleReadGroupInformationString()

        assert(expected.equals(actual))
    }

    @Test
    def testGetTophatReadGroupInformationString() {

        // Setup
        illuminaXMLReportReader.readGroupId = "ReadGroupId"
        setupXMLReader.sequencingCenter = "SequencingCenter"
        illuminaXMLReportReader.readLibrary = "ReadLibary"
        setupXMLReader.platform = "Platform"
        illuminaXMLReportReader.platformUnitId = "1"

        val expected: String = " --rg-id " + illuminaXMLReportReader.readGroupId + " --rg-sample " + sampleName + 
        " --rg-library " + illuminaXMLReportReader.readLibrary + " --rg-platform-unit " + illuminaXMLReportReader.platformUnitId + 
        " --rg-center " + setupXMLReader.sequencingCenter + " --rg-platform " + setupXMLReader.platform

        // Class under test
        val sample = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Run the test
        val actual: String = sample.getTophatStyleReadGroupInformationString()

        assert(expected.equals(actual))
    }

    @Test
    def testReference() {

        //Setup
        val expected: File = new File(baseTest.pathToReference)
        setupXMLReader.reference = expected

        // Class under test
        val sample = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Run the test
        val actual: File = sample.getReference()

        assert(expected == actual)
    }

    @Test
    def testEqualsTrue() {
        // Setup
        val expected = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Class under test
        val actual = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Run the test
        assert(actual.equals(expected))

    }

    @Test
    def testEqualsFalse() {
        // Setup
        val expected = new Sample("AnotherName", setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Class under test
        val actual = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Run the test
        assert(!actual.equals(expected))

    }

    @Test
    def testHashCode() {
        // Setup
        val expected = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Class under test
        val actual = new Sample(sampleName, setupXMLReader, illuminaXMLReportReader, 1, runFolderName)

        // Run the test
        assert(actual.hashCode() == expected.hashCode())

    }
}