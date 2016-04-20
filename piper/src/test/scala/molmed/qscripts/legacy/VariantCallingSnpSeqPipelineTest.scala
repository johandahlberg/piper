package molmed.qscripts.legacy

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.gatk.queue.pipeline._

/**
 * TODO
 * Implement cluster style tegatk as in AlignWithBwaSnpSeqPipelineTest
 */

class VariantCallingSnpSeqPipelineTest {

  val pathToScript = "-S src/main/scala/molmed/qscripts/VariantCalling.scala"

  val snpSeqBaseTest = SnpSeqBaseTest
  val resourceBasePath = "/local/data/gatk_bundle/b37/"


  var run: Boolean = false

  @BeforeClass
  @Parameters(Array("runpipeline"))
  def init(runpipeline: Boolean): Unit = {
    this.run = runpipeline
  }

  @Test
  def testBasicVariantCalling {
    val projectName = "test1"
    val testRawSNV = "TestProject.raw.snp.vcf"
    val testRawINDEL = "TestProject.raw.indel.vcf"
    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "VariantCallingPipeline"
    spec.args = Array(
      pathToScript,
      " -R " + snpSeqBaseTest.fullHumanGenome,
      " -i " + snpSeqBaseTest.chromosome20Bam,
      " --xml_input " + snpSeqBaseTest.pathToLegacySetupFile,
      " --dbsnp " + resourceBasePath + "dbsnp_137.b37.vcf",
      " --extra_indels " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " --extra_indels " + resourceBasePath + "1000G_phase1.indels.b37.vcf",
      " --hapmap " + resourceBasePath + "hapmap_3.3.b37.vcf",
      " --omni " + resourceBasePath + "1000G_omni2.5.b37.vcf",
      " --mills " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " -intervals " + "/local/data/gatk_bundle/b37/first1000SNPsonChr20.intervals",
      //" -outputDir " + "target/pipelinetests/VariantCallingPipeline/Shell/run/",
      " --nbr_of_threads 1 ",
      " --scatter_gather 1 ",
      " -noRecal ",
      " --test_mode ",
      " -startFromScratch ",
      " --project_name " + projectName).mkString
    spec.fileMD5s += testRawSNV -> "8cbdfc74848f95b7f753f9e636b92664"
    spec.fileMD5s += testRawINDEL -> "c19d75369ac288824bd678ab00aed74b"
    spec.run = run
    PipelineTest.executeTest(spec)

  }

  //  /**
  //   * Note that this test will never be run (takes to long) but it will at least check that the 
  //   * script compiles without intervals being given.
  //   */
  @Test
  def testNoIntervalsVariantCalling {
    val projectName = "test1"
    val testRawSNV = projectName + ".raw.snp.vcf"
    val testRawINDEL = projectName + ".raw.indel.vcf"
    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "VariantCallingPipeline"
    spec.args = Array(
      pathToScript,
      " -R " + snpSeqBaseTest.fullHumanGenome,
      " -i " + snpSeqBaseTest.chromosome20Bam,
      " --xml_input " + snpSeqBaseTest.pathToLegacySetupFile,
      " --dbsnp " + resourceBasePath + "dbsnp_137.b37.vcf",
      " --extra_indels " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " --extra_indels " + resourceBasePath + "1000G_phase1.indels.b37.vcf",
      " --hapmap " + resourceBasePath + "hapmap_3.3.b37.vcf",
      " --omni " + resourceBasePath + "1000G_omni2.5.b37.vcf",
      " --mills " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " -outputDir " + "target/pipelinetests/VariantCallingPipeline/Shell/run/",
      " --nbr_of_threads 1 ",
      " --scatter_gather 1 ",
      " -noRecal ",
      " --test_mode ",
      " -startFromScratch ",
      " --project_name  " + projectName).mkString

    spec.run = false
    PipelineTest.executeTest(spec)

  }
}
