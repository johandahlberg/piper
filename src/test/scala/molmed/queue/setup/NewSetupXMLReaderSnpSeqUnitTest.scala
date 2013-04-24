package molmed.queue.setup

import org.testng.annotations._
import org.testng.Assert
import java.io.File
import molmed.queue.SnpSeqBaseTest
import scala.collection.Seq
import molmed.queue.setup.stubs.IlluminaXMLReportReaderStub


class NewSetupXMLReaderSnpSeqUnitTest {

     /*
     * Note the these tests are dependent on the report.xml file, so if that is changed the tests need to be updated.
     */
    val baseTest = new SnpSeqBaseTest()
    val setupFile: File = new File("src/test/resources/testdata/exampleForNewSetupXML.xml")
    val setupXMLReader = new NewSetupXMLReader(setupFile)
    val sampleName = "1"
    val runFolderName = "src/test/resources/testdata/smallTestFastqDataFolder/report.xml"
        
        
    @BeforeMethod
    def beforeTest() {        
	    val baseTest = new SnpSeqBaseTest()
	    val setupFile: File = new File("src/test/resources/testdata/exampleForNewSetupXML.xml")
	    val setupXMLReader = new NewSetupXMLReader(setupFile)
	    val sampleName = "1"               
    }
    
    @AfterMethod
    def afterTest() {        
	    val baseTest = null
	    val setupFile: File = null
	    val setupXMLReader = null
	    val sampleName = null       
    }
    
//	@Test
//	def TestGetSampleFolder() = {        
//        val expected: File = new File("src/test/resources/testdata/smallTestFastqDataFolder/Sample_1").getAbsoluteFile()
//        val actual: File = setupXMLReader.getSampleFolder(sampleName, runFolderName)
//    	assert(actual == expected, "Failed TestGetSampleFolder() expected: " + expected + " actual: " + actual)    	
//	}    

	@Test
	def TestGetPlatform() = {		
	    val expected: String = "Illumina"
        val actual: String = setupXMLReader.getPlatform()
    	assert(actual.equals(expected))
	}

	@Test
	def TestGetSequencingCenter() = {
	    val expected: String = "SNP_SEQ_PLATFORM"
        val actual: String = setupXMLReader.getSequencingCenter()
    	assert(actual.equals(expected))	    
	}

	@Test
	def TestGetProjectName() = {
	    val expected: String = "Test project"
        val actual: String = setupXMLReader.getProjectName()
    	assert(actual.equals(expected))
	}

	@Test
	def TestGetSamples() = {
	    
	    val illuminaXMLReportReader: IlluminaXMLReportReaderAPI = new IlluminaXMLReportReaderStub()
	   
	    val actual:  Map[String, Seq[SampleAPI]] = setupXMLReader.getSamples()
	    
	    assert(actual.contains("1"))
	    assert(actual("1").size == 1)
	    assert(actual("1").isInstanceOf[Seq[SampleAPI]])
	    assert(actual.contains("2"))
	    assert(actual("2").size == 1)
	}
		
//	@Test
//	def TestGetSameSampleFromSeveralRunFolders() = {	   
//	    
//	    // Reset some of the shared resources
//	    val setupFile: File = new File(baseTest.pathToSetupFileForSameSampleAcrossMultipleRunFolders)
//	    val setupXMLReader = new NewSetupXMLReader(setupFile)
//	    val sampleName = "1"
//	    val runFolderName1  = "src/test/resources/testdata/runFoldersForMultipleSample/runfolder1/report.xml"
//	    val runFolderName2  = "src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/report.xml"
//	        
//	    // Setup the expected result - the same sample twice.    
//	    val illuminaXMLReportReader: IlluminaXMLReportReaderAPI = new IlluminaXMLReportReaderStub()
//	    val expected:  scala.collection.mutable.Map[String, Seq[SampleAPI]] = scala.collection.mutable.Map.empty[String, Seq[SampleAPI]]
//	    expected("1") = Seq(new Sample("1", setupXMLReader, illuminaXMLReportReader, 1, runFolderName1))
//	    expected("1") :+= new Sample("1", setupXMLReader, illuminaXMLReportReader, 1, runFolderName2)
//	    
//	    // Run the test and evaluate the result
//	    val actual:  Map[String, Seq[SampleAPI]] = setupXMLReader.getSamples()	    	 	    	    
//	    assert(expected.sameElements(actual))
//	}
//		
//	@Test
//	def TestGetSameSampleFromSeveralLanesInSameRunFolder() = {	   
//	    
//	    // Reset some of the shared resources
//	    val setupFile: File = new File(baseTest.pathToSetupFileForSameSampleAcrossMultipleLanes)
//	    val setupXMLReader = new NewSetupXMLReader(setupFile)
//	    val sampleName = "1"    
//	    val runFolderName1  = "src/test/resources/testdata/runFolderWithSameSampleInMultipleLanes/report.xml"
//	        
//	        
//	    // Setup the expected result - the same sample twice.    
//	    val illuminaXMLReportReader: IlluminaXMLReportReaderAPI = new IlluminaXMLReportReaderStub()
//	    val expected:  scala.collection.mutable.Map[String, Seq[SampleAPI]] = scala.collection.mutable.Map.empty[String, Seq[SampleAPI]]
//	    expected("1") = Seq(new Sample("1", setupXMLReader, illuminaXMLReportReader, 1, runFolderName1))
//	    expected("1") :+= new Sample("1", setupXMLReader, illuminaXMLReportReader, 1, runFolderName1)
//	    
//	    // Run the test and evaluate the result
//	    val actual:  Map[String, Seq[SampleAPI]] = setupXMLReader.getSamples()
//	    
//	    assert(expected.sameElements(actual))
//	}
	
	@Test
	def TestGetReference() = {
        val expected: File = new File("src/test/resources/testdata/exampleFASTA.fasta")
        val actual: File = setupXMLReader.getReference(sampleName)                
        assert(expected == actual)
	}
	
	@Test
	def TestGetUppmaxProjectId() = {
	    val expected: String = "a2009002"
        val actual: String = setupXMLReader.getUppmaxProjectId()
    	assert(actual.equals(expected))
	    
	}

}