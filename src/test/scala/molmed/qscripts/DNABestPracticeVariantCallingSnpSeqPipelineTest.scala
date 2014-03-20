package molmed.qscripts

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.sting.queue.pipeline._

class DNABestPracticeVariantCallingSnpSeqPipelineTest {

  val pathToScript = "-S src/main/scala/molmed/qscripts/DNABestPracticeVariantCalling.scala"

  val snpSeqBaseTest = SnpSeqBaseTest

  var run: Boolean = false

  val resourceBasePath = "/local/data/gatk_bundle/b37/"

  @BeforeClass
  @Parameters(Array("runpipeline"))
  def init(runpipeline: Boolean): Unit = {
    this.run = runpipeline
  }

  @Test()
  def testWholeGenome {
    //@TODO Specify pipeline outputs. 

    val co1156bam = "pipeline_output/processed_alignments/Co1156.clean.dedup.recal.bam"
    val co454bam = "pipeline_output/processed_alignments/Co454.clean.dedup.recal.bam"
    val rawSnps = "pipeline_output/variant_calls/piper_test.raw.snp.vcf"
    val rawIndels = "pipeline_output/variant_calls/piper_test.raw.indel.vcf"

    val spec = new PipelineTestSpec()
    spec.jobRunners = Seq("Shell")
    spec.name = "wgs_bestpracticevariantcalling"
    spec.args = Array(
      pathToScript,
      " --xml_input " + "/local/data/pipelineTestFolder/pipelineSetup.xml",
      " --dbsnp " + resourceBasePath + "dbsnp_137.b37.vcf",
      " --extra_indels " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " --extra_indels " + resourceBasePath + "1000G_phase1.indels.b37.vcf",
      " --hapmap " + resourceBasePath + "hapmap_3.3.b37.vcf",
      " --omni " + resourceBasePath + "1000G_omni2.5.b37.vcf",
      " --mills " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " -bwa " + "~/Bin/bwa-0.7.5a/bwa",
      " -samtools " + "~/Bin/samtools-0.1.19/samtools",
      " --number_of_threads 1 ",
      " --skip_recalibration ",
      " --scatter_gather 1 ",
      " -test ",
      " -startFromScratch "
      ).mkString
    spec.fileMD5s += co1156bam -> "d2e69f2cca454143a272ceb46a4022e8"
    spec.fileMD5s += co454bam -> "6cfca1908f301e804a4b6621d6afc849"
    spec.fileMD5s += rawSnps -> "8dd97afb4947841077b4336dd705603d"
    spec.fileMD5s += rawIndels -> "f521b96d2db8cba5cb5565a8f283a0f7"
    spec.run = this.run
    PipelineTest.executeTest(spec)
  }

  @Test()
  def testExome {

    val co1156bam = "pipeline_output/processed_alignments/Co1156.clean.dedup.recal.bam"
    val co454bam = "pipeline_output/processed_alignments/Co454.clean.dedup.recal.bam"
    val rawSnps = "pipeline_output/variant_calls/piper_test.raw.snp.vcf"
    val rawIndels = "pipeline_output/variant_calls/piper_test.raw.indel.vcf"
      
    val spec = new PipelineTestSpec()
    spec.jobRunners = Seq("Shell")
    spec.name = "exome_bestpracticevariantcalling"
    spec.args = Array(
      pathToScript,
      " --xml_input " + "/local/data/pipelineTestFolder/pipelineSetup.xml",
      " --isExome ",
      " --gatk_interval_file " + "/local/data/pipelineTestFolder/TruSeq_exome_targeted_regions-gatk.interval_list",
      " --dbsnp " + resourceBasePath + "dbsnp_137.b37.vcf",
      " --extra_indels " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " --extra_indels " + resourceBasePath + "1000G_phase1.indels.b37.vcf",
      " --hapmap " + resourceBasePath + "hapmap_3.3.b37.vcf",
      " --omni " + resourceBasePath + "1000G_omni2.5.b37.vcf",
      " --mills " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " -bwa " + "~/Bin/bwa-0.7.5a/bwa",
      " -samtools " + "~/Bin/samtools-0.1.19/samtools",
      " --skip_recalibration ",
      " --number_of_threads 1 ",
      " --scatter_gather 1 ",
      " -test ",
      " -startFromScratch "
      ).mkString

    spec.fileMD5s += co1156bam -> "e5e3dfc5e1759f8e277a87b053b8c5f7"
    spec.fileMD5s += co454bam -> "77d25515ce117aa16ac00cbafeae8e24"
    spec.fileMD5s += rawSnps -> "70d2dd0ed94b53dacc7575b1b59dc5d4"
    spec.fileMD5s += rawIndels -> "33f7fbf293d2cc816088473d9241f904"
    spec.run = this.run
    PipelineTest.executeTest(spec)
  }

}
