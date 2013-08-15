package molmed.queue.setup

import org.testng.annotations._
import org.testng.Assert
import molmed.queue.SnpSeqBaseTest
import molmed.queue.setup.stubs._
import java.io.File
import java.io.FileNotFoundException

class SampleSnpSeqUnitTest {

    val baseTest = SnpSeqBaseTest

    // Setup
    val readGroupId = "C0HNDACXX.1.1"
    val sequencingCenter = "SNP_SEQ_PLATFORM"
    val readLibrary = "CEP_C13-NA11992"
    val platform = "Illumina"
    val platformUnitId = "C0HNDACXX.1.1"

    val sampleName = "1"
    val reference = new File("src/test/resources/testdata/exampleFASTA.fasta").getAbsoluteFile()    
    val readGroupInfo = new ReadGroupInformation(sampleName, readGroupId, sequencingCenter, readLibrary, platform, platformUnitId)
    val readPairContainer = new ReadPairContainer(new File("src/test/resources/testdata/runFolderWithSameSampleInMultipleLanes/Sample_1/exampleFASTQ_L001_R1_file.fastq").getAbsoluteFile(),
        new File("src/test/resources/testdata/runFolderWithSameSampleInMultipleLanes/Sample_1/exampleFASTQ_L001_R2_file.fastq").getAbsoluteFile(),
        "1")

    val sample = new Sample(sampleName, reference, readGroupInfo, readPairContainer)

    @Test
    def testGetFastqs() {

        // Run the test
        val actual = sample.getFastqs()

        assert(actual == readPairContainer)
    }

    @Test
    def testGetBwaReadGroupInformationString() {

        val expected: String = "\"" + """@RG\tID:""" + readGroupId + """\\tSM:""" + sampleName +
            """\\tCN:""" + sequencingCenter + """\\tLB:""" + readLibrary +
            """\\tPL:""" + platform + """\\tPU:""" + platformUnitId + "\""

        // Run the test
        val actual: String = sample.getBwaStyleReadGroupInformationString()

        assert(expected.equals(actual))
    }

    @Test
    def testGetTophatReadGroupInformationString() {


        val expected: String = " --rg-id " + readGroupId + " --rg-sample " + sampleName +
            " --rg-library " + readLibrary + " --rg-platform-unit " + platformUnitId +
            " --rg-center " + sequencingCenter + " --rg-platform " + platform

        // Run the test
        val actual: String = sample.getTophatStyleReadGroupInformationString()

        assert(expected.equals(actual))
    }

    @Test
    def testReference() {

        // Run the test
        val actual: File = sample.getReference()

        assert(reference == actual)
    }
}