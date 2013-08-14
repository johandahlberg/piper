package molmed.queue

import java.io.File;

class SnpSeqBaseTest {
    
    // private static final String publicTestDirRelative = "public/testdata/";
    // public static final String publicTestDir = new File(publicTestDirRelative).getAbsolutePath() + "/";
    // protected static final String publicTestDirRoot = publicTestDir.replace(publicTestDirRelative, "");
    
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
    //val pathSetupFile: String = pathToBaseDir + "pipelineSetup.xml"
    val pathToSetupFileForSameSampleAcrossMultipleRunFolders = pathToBaseDir + "pipelineSetupSameSampleAcrossMultipleRunFolders.xml"
    val pathToSetupFileForSameSampleAcrossMultipleLanes = pathToBaseDir + "pipelineSetupSameSampleAcrossMultipleLanes.xml"
    
    val pathToReportXML: String = pathToBaseDir + "smallTestFastqDataFolder/report.xml"    
    val pathToReportXMLForSameSampleAcrossMultipleLanes = pathToBaseDir +"runFolderWithSameSampleInMultipleLanes/report.xml"

}