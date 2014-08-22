package molmed.qscripts.legacy

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.gatk.queue.pipeline._

/**
 * TODO
 * Implement cluster style tegatk as in AlignWithBwaSnpSeqPipelineTest
 */

class CufflinksSnpSeqPipelineTest {

  val pathToScript = "-S src/main/scala/molmed/qscripts/Cufflinks.scala"

  val snpSeqBaseTest = SnpSeqBaseTest

  var run: Boolean = false

  @BeforeClass
  @Parameters(Array("runpipeline"))
  def init(runpipeline: Boolean): Unit = {
    this.run = runpipeline
  }

  @Test
  def testBasicCufflinks {
    val projectName = "test1"

    val transcripts = "cufflinks/Pairend_StrandSpecific_51mer_Human_hg19.with.rg/transcripts.gtf"
    val genesFpkmTracking = "cufflinks/Pairend_StrandSpecific_51mer_Human_hg19.with.rg/genes.fpkm_tracking"

    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "BasicCufflinks"
    spec.args = Array(
      pathToScript,
      " -i " + snpSeqBaseTest.pathToCuffdiffCohortFile,
      " --xml_input " + snpSeqBaseTest.pathToLegacySetupFile,
      " --reference " + snpSeqBaseTest.hg19,
      " --annotations " + snpSeqBaseTest.hg19annotations,
      " --path_to_cufflinks " + snpSeqBaseTest.pathToCufflinks,
      " -startFromScratch ").mkString

    spec.fileMD5s += transcripts -> "25d32dd6c9833687aa18b96383a0b088"
    spec.fileMD5s += genesFpkmTracking -> "cd9619944c297f98595cf5838466e8c7"

    spec.run = run
    PipelineTest.executeTest(spec)
  }

  // @TODO Excluding this from the workflow for now as it takes way to long to run.
  // plust that it seems experimental at best. Maybe this will be tested in the future.
  //  @Test
  //  def testFindNovelCufflinks {
  //    val projectName = "test1"
  //
  //    val transcripts = "cufflinks/Pairend_StrandSpecific_51mer_Human_hg19/transcripts.gtf"
  //    val genesFpkmTracking = "cufflinks/Pairend_StrandSpecific_51mer_Human_hg19/genes.fpkm_tracking"
  //
  //    val spec = new PipelineTestSpec
  //    spec.jobRunners = Seq("Shell")
  //    spec.name = "FindNovelCufflinks"
  //    spec.args = Array(
  //      pathToScript,
  //      " -i " + snpSeqBaseTest.pathToRNAtestBam,
  //      " --reference " + snpSeqBaseTest.hg19,
  //      " --annotations " + snpSeqBaseTest.hg19annotations,
  //      " --path_to_cufflinks " + snpSeqBaseTest.pathToCufflinks,
  //      " --findNovel ",
  //      " --merge ",
  //      " -startFromScratch ").mkString
  //
  //    spec.fileMD5s += transcripts -> "25d32dd6c9833687aa18b96383a0b088"
  //    spec.fileMD5s += genesFpkmTracking -> "cd9619944c297f98595cf5838466e8c7"
  //
  //    PipelineTest.executeTest(spec, run)
  //  }
}
