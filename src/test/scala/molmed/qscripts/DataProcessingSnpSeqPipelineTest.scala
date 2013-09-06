package molmed.qscripts

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.sting.queue.pipeline._

/**
 * TODO
 * Implement cluster style testing as in AlignWithBwaSnpSeqPipelineTest
 */

class DataProcessingSnpSeqPipelineTest {

    val pathToScript = "-S src/main/scala/molmed/qscripts/DataProcessingPipeline.scala"

    val snpSeqBaseTest = SnpSeqBaseTest

    var run: Boolean = false

    @BeforeClass
    @Parameters(Array("runpipeline"))
    def init(runpipeline: Boolean): Unit = {
        this.run = runpipeline
    }

    @Test(timeOut = 36000000)
    def testSimpleBAM {
        val projectName = "test1"
        val testOut = projectName + ".exampleBAM.clean.dedup.recal.bam"
        val spec = new PipelineTestSpec
        spec.jobRunners = Seq("Shell")
        spec.name = "DataProcessingPipeline"
        spec.args = Array(
            pathToScript,
            " -R " + snpSeqBaseTest.publicTestDir + "exampleFASTA.fasta",
            " -i " + snpSeqBaseTest.publicTestDir + "exampleBAM.bam",
            " -D " + snpSeqBaseTest.publicTestDir + "exampleDBSNP.vcf",
            " -test ",
            " -startFromScratch ",
            " -p " + projectName).mkString
        spec.fileMD5s += testOut -> "8c79ed658852b2f3a26fce6f35455763"
        PipelineTest.executeTest(spec, run)
    }

    @Test(timeOut = 36000000)
    def testBWAPEBAM {
        val projectName = "test2"
        val testOut = projectName + ".exampleBAM.clean.dedup.recal.bam"
        val spec = new PipelineTestSpec
        spec.jobRunners = Seq("Shell")
        spec.name = "DataProcessingPipeline"
        spec.args = Array(
            pathToScript,
            " -R " + snpSeqBaseTest.publicTestDir + "exampleFASTA.fasta",
            " -i " + snpSeqBaseTest.publicTestDir + "exampleBAM.bam",
            " -D " + snpSeqBaseTest.publicTestDir + "exampleDBSNP.vcf",
            " -test ",
            " --realign ",
            " -bwa /usr/bin/bwa",
            " -bwape ",
            " -startFromScratch ",
            " -p " + projectName).mkString
        spec.fileMD5s += testOut -> "34b8523546a6bc62af4b59ac2ee6b370"
        PipelineTest.executeTest(spec, run)
    }

    @Test
    def testBWAPEBAMWithRevert {
        val projectName = "test2"
        val testOut = projectName + ".exampleBAM.clean.dedup.recal.bam"
        val spec = new PipelineTestSpec
        spec.jobRunners = Seq("Shell")
        spec.name = "DataProcessingPipeline"
        spec.args = Array(
            pathToScript,
            " -R " + snpSeqBaseTest.publicTestDir + "exampleFASTA.fasta",
            " -i " + snpSeqBaseTest.publicTestDir + "exampleBAM.bam",
            " -D " + snpSeqBaseTest.publicTestDir + "exampleDBSNP.vcf",
            " -test ",
            " --realign ",
            " -bwa /usr/bin/bwa",
            " -bwape ",
            " --revert ",
            " -startFromScratch ",
            " -p " + projectName).mkString
        spec.fileMD5s += testOut -> "a3d8852cfb07ac898cb6c75c4a90c372"
        PipelineTest.executeTest(spec, run)
    }

}
