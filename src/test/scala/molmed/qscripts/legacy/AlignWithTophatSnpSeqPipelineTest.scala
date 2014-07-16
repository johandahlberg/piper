package molmed.qscripts.legacy

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.gatk.queue.pipeline._

/**
 * TODO
 * Implement cluster style tegatk as in AlignWithBwaSnpSeqPipelineTest
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
      " -xi " + snpSeqBaseTest.pathSetupFile,
      " -tophat " + "/usr/local/bin/tophat2",
      " -samtools " + "~/Bin/samtools-0.1.19/samtools",
      " -startFromScratch ").mkString
    spec.fileMD5s += testBam -> "e8c0967797d57f28b308efce5ef7d970"
    spec.run = run
    PipelineTest.executeTest(spec)

  }
}
