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
      " --project_name " + projectName).mkString
    spec.fileMD5s += testOut -> "cff2cbe5cd411f70054989a1006ec436"
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
      " --project_name " + projectName).mkString
    spec.fileMD5s += testOut -> "97f254cf538a4e8d183d7cd29c42ba45"
    spec.run = run
    PipelineTest.executeTest(spec)
  }

  @Test
  def testBWAPEBAMWithRevert {
    val projectName = "test3"
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
      " --project_name " + projectName).mkString
    spec.fileMD5s += testOut -> "3a6c2b891e4834ebd14a3a902fbb758d"
    spec.run = run
    PipelineTest.executeTest(spec)
  }

}
