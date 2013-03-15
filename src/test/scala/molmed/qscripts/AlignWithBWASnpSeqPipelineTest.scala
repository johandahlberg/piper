package molmed.qscripts

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.sting.queue.pipeline._

/**
 * Test class for the AlignWithBwa qscript.
 * Each test will have a data provider, supplying the correct environment setup (containing the commandline, jobrunner and bwa path),
 * and correct md5 check sums for the result file. The result files will differ in the different environments due to the different
 * bwa versions used.
 * 
 */
class AlignWithBWASnpSeqPipelineTest {
    
    // If these tests are to run with the drmaa jobrunner, etc, specify -Dpipline.uppmax=true on the command line
    val runOnUppmax =  System.getProperty("pipeline.uppmax") == "true"
    
    val snpSeqBaseTest: SnpSeqBaseTest = new SnpSeqBaseTest()
    val pathToScript: String = " -S src/main/scala/molmed/qscripts/AlignWithBWA.scala"    
    
    val walltime = 600    
    
    case class EnvironmentSetup(commandline: String, jobrunner: Seq[String], pathToBwa: String) {}    

    /**
     * testPairEndAlignment
     */
    @DataProvider(name = "testPairEndAlignmentDataProvider")
    def testPairEndAlignmentDataProvider: Array[Array[Object]] = {        
        runOnUppmax match {
            case true => {
                val envSetup = EnvironmentSetup(pathToScript, Seq("Drmaa"), "/bubo/sw/apps/bioinfo/bwa/0.6.2/kalkyl/bwa");
                //TODO Fix new md5s
                val md5 = "88d073bc43b6c019653787f58628c744"                
                Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
            }
            case _ => {
                val envSetup = EnvironmentSetup(pathToScript, Seq("Shell"), "/usr/bin/bwa");
                val md5 = "6c9333a9ab0266758b2eb6a28e8438a7"
                Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
            }
        }                     							   
    }        							    
        							    
    @Test(dataProvider="testPairEndAlignmentDataProvider")
    def testPairedEndAlignment(envSetup: EnvironmentSetup, md5sum: String) = {
    	val projectName = "test"
    	val testOut = "1.bam"
	    val spec = new PipelineTestSpec()
	  
	    spec.jobRunners = envSetup.jobrunner
	    
	    spec.name = "AlignPairedEndWithBwa"
	    spec.args = Array(envSetup.commandline,
	            		  " -bwa " + envSetup.pathToBwa,
	    				  " -i " + snpSeqBaseTest.pathSetupFile,
	    				  " -bwape ",
	    				  " -wallTime " + walltime,
	    				  " -startFromScratch ").mkString
	    spec.fileMD5s += testOut -> md5sum
	    PipelineTest.executeTest(spec)
    }
    
   /**
    * testSingleEndAlignment 
    */
    
    @DataProvider(name = "testSingleEndAlignmentDataProvider")
    def testSingleEndAlignmentDataProvider: Array[Array[Object]] = {        
        runOnUppmax match {
            case true => {
                val envSetup = EnvironmentSetup(pathToScript, Seq("Drmaa"), "/bubo/sw/apps/bioinfo/bwa/0.6.2/kalkyl/bwa");
                //TODO Fix new md5s
                val md5 = "4f5aa4cff97c7940ca17e552cf499817"                
                Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
            }
            case _ => {
                val envSetup = EnvironmentSetup(pathToScript, Seq("Shell"), "/usr/bin/bwa");
                val md5 = "2de23711aac1a82639c6c827a7e500dd"
                Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
            }
        }                     							   
    }
 
  @Test(dataProvider="testSingleEndAlignmentDataProvider")
  def testSingleEndAlignment(envSetup: EnvironmentSetup, md5sum: String) {
      val projectName = "test"
      val testOut = "1.bam"
      val spec = new PipelineTestSpec()
  
      spec.jobRunners = envSetup.jobrunner
    
      spec.name = "AlignSingleEndWithBwa"
      spec.args = Array(envSetup.commandline,
    		  			" -bwa " + envSetup.pathToBwa,
    		  			" -i " + snpSeqBaseTest.pathSetupFile,
    		  			" -bwase ",
    		  			" -wallTime " + walltime,
    				  	" -startFromScratch ").mkString
      spec.fileMD5s += testOut -> md5sum
      PipelineTest.executeTest(spec)
  }
 
    /**
    * testBwaSWAlignment 
    */
    
    @DataProvider(name = "testBwaSWAlignmentDataProvider")
    def testBwaSWAlignmentDataProvider: Array[Array[Object]] = {        
        runOnUppmax match {
            case true => {
                val envSetup = EnvironmentSetup(pathToScript, Seq("Drmaa"), "/bubo/sw/apps/bioinfo/bwa/0.6.2/kalkyl/bwa");                                
                val md5 = "d5300404fde12c139a9e9e8b1c09b304"                
                Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
            }
            case _ => {
                val envSetup = EnvironmentSetup(pathToScript, Seq("Shell"), "/usr/bin/bwa");
                val md5 = "d5300404fde12c139a9e9e8b1c09b304"
                Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
            }
        }                     							   
    }
  
  @Test(dataProvider="testBwaSWAlignmentDataProvider")
  def testBwaSWAlignment(envSetup: EnvironmentSetup, md5sum: String) {
    val projectName = "test"
    val testOut = "1.bam"
    val spec = new PipelineTestSpec()
  
    spec.jobRunners = envSetup.jobrunner
    
    spec.name = "AlignSWWithBwa"
    spec.args = Array(envSetup.commandline,
            		  " -bwa " + envSetup.pathToBwa,
    				  " -i " + snpSeqBaseTest.pathSetupFile,
    				  " -bwasw ",
    				  " -wallTime " + walltime,
    				  " -startFromScratch ").mkString
    spec.fileMD5s += testOut -> md5sum
    PipelineTest.executeTest(spec)
  }
  
  
/**
 * TODO: The following tests need to be reimagined in a later version of piper.
 * They are not working right now because of the undeteministic nature of the 
 * output.  
 */
  
  
//    /**
//    * testSameSampleInMoreThanOneRunFolder 
//    */
//    
//  @DataProvider(name = "testSameSampleInMoreThanOneRunFolderDataProvider")
//  def SameSampleInMoreThanOneRunFolderDataProvider: Array[Array[Object]] = {        
//    runOnUppmax match {
//        case true => {
//            val envSetup = EnvironmentSetup(pathToScript, Seq("Drmaa"), "/bubo/sw/apps/bioinfo/bwa/0.6.2/kalkyl/bwa");
//            //TODO Fix new md5s
//            val md5 = "8affd69d2b506bd7d35bdd226f27d057"                
//            Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
//        }
//        case _ => {
//            val envSetup = EnvironmentSetup(pathToScript, Seq("Shell"), "/usr/bin/bwa");
//            val md5 = Seq("d8867a7264261573a15793dead163d24", "3a08932e07599ccb7b1c9ee5603fe469", "c106f36d82c2fe72d72308d77ce085bd")
//            Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
//        }
//    }                     							   
//  }
//
//  @Test(dataProvider="testSameSampleInMoreThanOneRunFolderDataProvider")
//  def SameSampleInMoreThanOneRunFolder(envSetup: EnvironmentSetup, md5sum: Seq[String]) {
//    val projectName = "test"
//    val testOut = "1.bam"
//    val spec = new MultipleOutcomeTestSpec()
//  
//    spec.jobRunners = envSetup.jobrunner
//    
//    spec.name = "SameSampleInMoreThanOneRunFolder"
//    spec.args = Array(envSetup.commandline,
//            		  " -bwa " + envSetup.pathToBwa,
//    				  " -i " + snpSeqBaseTest.pathToSetupFileForSameSampleAcrossMultipleRunFolders,
//    				  " -bwape ",
//    				  " -wallTime " + walltime,
//    				  " -startFromScratch ").mkString
//    spec.fileMD5s += testOut -> md5sum
//    PipelineTest.executeTest(spec)
//  } 
//  
//  
//  /**
//   * testSameSampleAcrossSeveralLanes
//   */
//    
//  //TODO Fix this!
//  @DataProvider(name = "testSameSampleAcrossSeveralLanesDataProvider")
//  def testSameSampleAcrossSeveralLanesDataProvider: Array[Array[Object]] = {        
//    runOnUppmax match {
//        case true => {
//            val envSetup = EnvironmentSetup(pathToScript, Seq("Drmaa"), "/bubo/sw/apps/bioinfo/bwa/0.6.2/kalkyl/bwa");
//            //TODO Fix new md5s
//            val md5 = Seq("")                
//            Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
//        }
//        case _ => {
//            val envSetup = EnvironmentSetup(pathToScript, Seq("Shell"), "/usr/bin/bwa");
//            val md5 = Seq("04c19d2a45b49f9a8bcf4e0289421ab6", "6f8020376e7d6e46a231bc3efa0e9fa1", "0ed6b1302636d6b5d62e89416c6283bc")
//            Array(Array(envSetup, md5)).asInstanceOf[Array[Array[Object]]]
//        }
//    }                     							   
//  }
// 
//  //TODO Fix this!
//  @Test(dataProvider="testSameSampleAcrossSeveralLanesDataProvider")
//  def testSameSampleAcrossSeveralLanes(envSetup: EnvironmentSetup, md5sum: Seq[String]) {
//    val projectName = "test"
//    val testOut = "1.bam"
//    val spec = new MultipleOutcomeTestSpec()
//  
//    spec.jobRunners = envSetup.jobrunner
//    
//    spec.name = "SameSampleAcrossSeveralLanes"
//    spec.args = Array(envSetup.commandline,
//            		  " -bwa " + envSetup.pathToBwa,
//    				  " -i " + snpSeqBaseTest.pathToSetupFileForSameSampleAcrossMultipleLanes,
//    				  " -bwape ",
//    				  " -wallTime " + walltime,
//    				  " -startFromScratch ").mkString
//    spec.fileMD5s += testOut -> md5sum
//    PipelineTest.executeTest(spec)
//  } 
}