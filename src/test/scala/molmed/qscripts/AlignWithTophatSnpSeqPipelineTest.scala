package molmed.qscripts

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.sting.queue.pipeline._

/**
 * TODO
 * Implement cluster style testing as in AlignWithBwaSnpSeqPipelineTest
 */

class AlignWithTophatSnpSeqPipelineTest {

    val pathToScript = "-S src/main/scala/molmed/qscripts/AlignWithTophat.scala"

    val snpSeqBaseTest = SnpSeqBaseTest

    var run: Boolean = false

    @BeforeClass
    @Parameters(Array("runpipeline"))
    def init(runpipeline: Boolean): Unit = {
        this.run = runpipeline
    }

    @Test
    def testBasicAlignWithTophat {
        val projectName = "test1"
        val testBam = "1/accepted_hits.bam"
        val spec = new PipelineTestSpec
        spec.jobRunners = Seq("Shell")
        spec.name = "AlignWithTophatPipeline"
        spec.args = Array(
            pathToScript,
            " -i " + snpSeqBaseTest.pathSetupFile,
            " -tophat " + "/usr/local/bin/tophat2",
            " -samtools " + "/usr/bin/samtools",
            " -startFromScratch ").mkString
        spec.fileMD5s += testBam -> "3cb50b0cf1d39c303eccfd74bfff62dc"
        PipelineTest.executeTest(spec, run)
    }
}
