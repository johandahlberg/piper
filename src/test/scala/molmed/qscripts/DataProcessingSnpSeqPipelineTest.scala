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
    spec.fileMD5s += testOut -> "899cb08f77f4569802dfb4a88d96a65e"
    spec.run = this.run
    PipelineTest.executeTest(spec)
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
    spec.fileMD5s += testOut -> "5dfa965afe257c4d29ccb5c0b4198633"
    spec.run = run
    PipelineTest.executeTest(spec)
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
    spec.fileMD5s += testOut -> "899cb08f77f4569802dfb4a88d96a65e"
    spec.run = run
    PipelineTest.executeTest(spec)
  }

}
