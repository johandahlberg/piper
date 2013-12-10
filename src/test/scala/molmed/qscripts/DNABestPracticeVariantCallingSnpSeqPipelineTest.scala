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
    
    val spec = new PipelineTestSpec()
    spec.jobRunners = Seq("Shell")
    spec.name = "bestpracticevariantcalling"
    spec.args = Array(
      pathToScript,
      " --xml_input " + "/local/data/pipelineTestFolder/pipelineSetup.xml",
      " --dbsnp " + resourceBasePath + "dbsnp_137.b37.vcf",
      " --extra_indels " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " --extra_indels " + resourceBasePath + "1000G_phase1.indels.b37.vcf",
      " --hapmap " + resourceBasePath + "hapmap_3.3.b37.vcf",
      " --omni " + resourceBasePath + "1000G_omni2.5.b37.vcf",
      " --mills " + resourceBasePath + "Mills_and_1000G_gold_standard.indels.b37.vcf",
      " -bwa " + "/usr/bin/bwa",
      " -samtools " + "/usr/bin/samtools",
      " --number_of_threads 1 ",
      " --skip_recalibration ",
      " --scatter_gather 1 ",
      " -test ",
      " -startFromScratch ").mkString
    //spec.fileMD5s += testOut -> "8d4732ed7e161c20fadb1618e30d73df"
    spec.run = this.run
    PipelineTest.executeTest(spec)
  }
  
    @Test()
  def testExome {
    //@TODO Specify pipeline outputs. 
    
    val spec = new PipelineTestSpec()
    spec.jobRunners = Seq("Shell")
    spec.name = "bestpracticevariantcalling"
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
      " -bwa " + "/usr/bin/bwa",
      " -samtools " + "/usr/bin/samtools",
      " --number_of_threads 1 ",
      " --skip_recalibration ",
      " --scatter_gather 1 ",
      " -test ",
      " -startFromScratch ").mkString
    //spec.fileMD5s += testOut -> "8d4732ed7e161c20fadb1618e30d73df"
    spec.run = this.run
    PipelineTest.executeTest(spec)
  }

}