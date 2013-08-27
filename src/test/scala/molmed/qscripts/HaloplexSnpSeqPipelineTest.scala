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
    val snpSeqBaseTest = SnpSeqBaseTest
    var run: Boolean = false

    @BeforeClass
    @Parameters(Array("runpipeline"))
    def init(runpipeline: Boolean): Unit = {
        this.run = runpipeline
    }

    @Test
    def testBasicHaloplex {
        val testRawVcf = "vcf_files/TestProject.vcf"
        val testRawFilteredVcf = "vcf_files/TestProject.filtered.vcf"
        
        val spec = new PipelineTestSpec
        spec.jobRunners = Seq("Shell")
        spec.name = "HaloplexPipeline"
        spec.args = Array(
            pathToScript,
            " -res " + "/local/data/gatk_bundle/hg19/",
            " -i " + snpSeqBaseTest.pathHaloplexSetupFile,
            " -intervals " + "/local/data/haloplex_test_data/design_files/test.roi.bed",
            " --amplicons " + "/local/data/haloplex_test_data/design_files/test.selection.bed",
            " -bwa " + "/usr/bin/bwa",
            " -cutadapt " + "/usr/local/bin/cutadapt",
            " --path_to_sync " + "$HOME/workspace/piper/resources/FixEmptyReads.pl",
            " -outputDir " + PipelineTest.runDir(spec.name, spec.jobRunners(0)),
            " --nbr_of_threads 1 ",
            " --scatter_gather 1 ",
            " --test_mode ",
            " -startFromScratch ").mkString
        spec.fileMD5s += testRawVcf -> "442f5b2bf28dda15e63ec6b969b59366"
        spec.fileMD5s += testRawFilteredVcf -> "1ecaac655d86626f9c28d677dc3f86fb"
        
        PipelineTest.executeTest(spec, run)
    }
}
