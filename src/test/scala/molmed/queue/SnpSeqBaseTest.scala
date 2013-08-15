package molmed.queue

import java.io.File;

object SnpSeqBaseTest {
    
	// Place on local file system to run tests (otherwise the will end up in the workspace (which by default
    // on molmed is network mounted (which makes for some slowwwww tests).  
	val localTestScratch = new File("/local/scratch/pipeline_test_scratch")
      
    val pathToBundle: String = "/local/data/gatk_bundle/b37"
    val chromosome20Bam = "/local/data/gatk_bundle/b37/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20.bam"
    val fullHumanGenome = "/local/data/gatk_bundle/b37/human_g1k_v37.fasta"
    val hg19 = "/local/data/gatk_bundle/hg19/ucsc.hg19.fasta"  
    
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
    
    val pathToReportXML: String = pathToBaseDir + "smallTestFastqDataFolder/report.xml"    
    val pathToReportXMLForSameSampleAcrossMultipleLanes = pathToBaseDir +"runFolderWithSameSampleInMultipleLanes/report.xml"

}