package molmed.qscripts

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.sting.queue.pipeline._

/**
 * TODO
 * Implement cluster style testing as in AlignWithBwaSnpSeqPipelineTest
 */

class HaloplexSnpSeqPipelineTest {

    val pathToScript = "-S src/main/scala/molmed/qscripts/Haloplex.scala"
    val snpSeqBaseTest = new SnpSeqBaseTest()
    var run: Boolean = false

    @BeforeClass
    @Parameters(Array("runpipeline"))
    def init(runpipeline: Boolean): Unit = {
        this.run = runpipeline
    }

    @Test
    def testBasicHaloplex {
        val projectName = "test1"
        val testRawSNV = projectName + ".raw.snv.vcf"
        val testRawINDEL = projectName + ".raw.indel.vcf"
        
        val spec = new PipelineTestSpec
        spec.jobRunners = Seq("Shell")
        spec.name = "HaloplexPipeline"
        spec.args = Array(
            pathToScript,
            " -res " + "/local/data/gatk_bundle/hg19/",
            " -i " + snpSeqBaseTest.pathHaloplexSetupFile,
            " -intervals " + "/local/data/haloplex_test_data/design_files/test.roi.bed",
            " --amplicons " + "/local/data/haloplex_test_data/design_files/test.selection.bed",
            " -outputDir " + "target/pipelinetests/HaloplexPipeline/Shell/run/",
            " -bwa " + "/usr/bin/bwa",
            " -cutadapt " + "/usr/local/bin/cutadapt",
            " --path_to_sync " + "$HOME/workspace/piper/resources/FixEmptyReads.pl",
            " --nbr_of_threads 8 ",
            " --scatter_gather 1 ",
            " --test_mode ",
            " -startFromScratch ").mkString
        spec.fileMD5s += testRawSNV -> ""
        spec.fileMD5s += testRawINDEL -> ""
        
        PipelineTest.executeTest(spec, run)
    }
}
