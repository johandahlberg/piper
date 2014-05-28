package molmed.queue

import java.io.File;

object SnpSeqBaseTest {

  // Place on local file system to run tests (otherwise the will end up in the workspace (which by default
  // on molmed is network mounted (which makes for some slowwwww tests).  
  val localTestScratch = new File("/local/scratch/pipeline_test_scratch")

  val pathToBundle: String = "/local/test_data/piper_references/gatk_bundle/2.8/b37"
  val chromosome20Bam = "/local/test_data/piper_references/gatk_bundle/2.8/b37//NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20.bam"
  val fullHumanGenome = "/local/test_data/piper_references/gatk_bundle/2.8/b37//human_g1k_v37.fasta"
  val hg19 = "/local/test_data/piper_references/gatk_bundle/hg19/ucsc.hg19.fasta"

  val pathToBaseDir: String = "src/test/resources/testdata/"
  val publicTestDir: String = new File("src/test/resources/testdata").getAbsolutePath() + "/"

  val pathToSampleFolder: String = pathToBaseDir + "smallTestFastqDataFolder/Sample_1"

  val pathToMate1: String = pathToBaseDir + "smallTestFastqDataFolder/Sample_1/exampleFASTQ_L001_R1_file.fastq"
  val pathToMate2: String = pathToBaseDir + "smallTestFastqDataFolder/Sample_1/exampleFASTQ_L001_R2_file.fastq"

  val pathToReference: String = pathToBaseDir + "exampleFASTA.fasta"

  val pathSetupFile: String = pathToBaseDir + "exampleForNewSetupXML.xml"
  val pathHaloplexSetupFile: String = pathToBaseDir + "exampleHaloplexSetupXML.xml"
  val pathLegacySetupFile: String = pathToBaseDir + "pipelineSetup.xml"
  val pathToSetupFileForSameSampleAcrossMultipleRunFolders = pathToBaseDir + "pipelineSetupSameSampleAcrossMultipleRunFolders.xml"
  val pathToSetupFileForSameSampleAcrossMultipleLanes = pathToBaseDir + "pipelineSetupSameSampleAcrossMultipleLanes.xml"

  val differentRgsCohortList = pathToBaseDir + "bam.cohort.list"
  val singleFileCohortList = pathToBaseDir + "bam.single.file.cohort.list"

  val pathToFlatFileReport: String = pathToBaseDir + "report.tsv"

  val pathToReportXML: String = pathToBaseDir + "smallTestFastqDataFolder/report.xml"
  val pathToReportXMLForSameSampleAcrossMultipleLanes = pathToBaseDir + "runFolderWithSameSampleInMultipleLanes/report.xml"
  val pathToReportWithMultipleLibraries = pathToBaseDir + "reportWithMultipleLibraryNames.xml"

  val rnaSeqBaseDir = "/local/data/rna_seq_test_data/"
  val pathToRNAtestBam = rnaSeqBaseDir + "Pairend_StrandSpecific_51mer_Human_hg19.reordered.with.rg.sorted.bam"
  val pathToCuffdiffCohortFile = pathToBaseDir + "cuffdiff.cohort.list"
  val pathToCufflinksCohortFile = pathToBaseDir + "cufflinks.cohort.list"
  val hg19_rRNA = rnaSeqBaseDir + "hg19_rRNA.bed"
  val pathToReplicatesFile = "" // @TODO
  val hg19annotations = rnaSeqBaseDir + "Homo_sapiens/UCSC/hg19/Annotation/Archives/archive-2013-03-06-11-23-03/Genes/genes.gtf"

  val pathToCuffdiff = "/local/programs/cufflinks-2.1.1.Linux_x86_64/"
  val pathToCufflinks = "/local/programs/cufflinks-2.1.1.Linux_x86_64/"

  val pathToRNASeQC = "/home/MOLMED/patsm159/workspace/piper/resources/RNA-SeQC_v1.1.7.jar"

}