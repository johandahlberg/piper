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
      " -res " + "/local/data/gatk_bundle/b37/",
      " --xml_input " + snpSeqBaseTest.pathHaloplexSetupFile,
      " -intervals " + "/local/data/haloplex_test_data/design_files/test.roi.bed",
      " --amplicons " + "/local/data/haloplex_test_data/design_files/test.selection.bed",
      " -bwa " + "~/Bin/bwa-0.7.5a/bwa",
      " -cutadapt " + "/usr/local/bin/cutadapt",
      " --path_to_sync " + "$HOME/workspace/piper/resources/FixEmptyReads.pl",
      " -outputDir " + PipelineTest.runDir(spec.name, spec.jobRunners(0)),
      " --nbr_of_threads 1 ",
      " --scatter_gather 1 ",
      " --test_mode ",
      " -startFromScratch ").mkString
    spec.fileMD5s += testRawVcf -> "d3d6ed9bbe58d670e8d4f2ad60f3b685"
    spec.fileMD5s += testRawFilteredVcf -> "5b3575d7093bd6e8001f27d2e5535ba8"

    spec.run = run
    PipelineTest.executeTest(spec)
  }

  @Test
  def testOnlyAligmentHaloplex {
    val testRawBam = "bam_files/1.C0HNDACXX.1.1.bam"

    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "HaloplexPipeline"
    spec.args = Array(
      pathToScript,
      " -res " + "/local/data/gatk_bundle/b37/",
      " --xml_input " + snpSeqBaseTest.pathHaloplexSetupFile,
      " -intervals " + "/local/data/haloplex_test_data/design_files/test.roi.bed",
      " --amplicons " + "/local/data/haloplex_test_data/design_files/test.selection.bed",
      " -bwa " + "~/Bin/bwa-0.7.5a/bwa",
      " -cutadapt " + "/usr/local/bin/cutadapt",
      " --path_to_sync " + "$HOME/workspace/piper/resources/FixEmptyReads.pl",
      " -outputDir " + PipelineTest.runDir(spec.name, spec.jobRunners(0)),
      " --nbr_of_threads 1 ",
      " --scatter_gather 1 ",
      " --test_mode ",
      " -startFromScratch ",
      " --onlyAlignments ").mkString
    spec.fileMD5s += testRawBam -> "eaedd6117bf9fe8d94be613f5662e6bb"

    spec.run = run
    PipelineTest.executeTest(spec)
  }
}
