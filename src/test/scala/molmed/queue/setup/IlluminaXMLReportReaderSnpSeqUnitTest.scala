package molmed.queue.setup

import org.testng.annotations._
import org.testng.Assert
import java.io.File
import molmed.queue.SnpSeqBaseTest

class IlluminaXMLReportReaderSnpSeqUnitTest {
       
    /*
     * Note the these tests are dependent on the report.xml file, so if that is changed the tests need to be updated.
     */
    val baseTest = new SnpSeqBaseTest()
    val reportFile: File = new File(baseTest.pathToReportXML)
    val illuminaXMLReportReader = new IlluminaXMLReportReader(reportFile)
    val sampleName = "1" 
    val lane = 1
    val flowcellId = "C0HNDACXX"

    @Test
    def testGetLanes{        
        val expected: List[Int] = List(1)
        val actual: List[Int] = illuminaXMLReportReader.getLanes(sampleName)               
    	assert(actual.equals(expected))
    }
    
   
    @Test
    def testGetLanesMoreThanOneLaneForSample{        
        val expected: List[Int] = List(1,2)
        
        val illuminaXMLReportReaderForFileWithMoreThanOneLanePerSample = 
            new IlluminaXMLReportReader(new File(baseTest.pathToReportXMLForSameSampleAcrossMultipleLanes))
        
        val actual: List[Int] = illuminaXMLReportReaderForFileWithMoreThanOneLanePerSample.getLanes(sampleName)               
        
    	assert(actual.equals(expected))
    }
            
    @Test
    def testGetReadLibrary() {        
        val expected: String = "CEP_C13-NA11992"
        val actual: String = illuminaXMLReportReader.getReadLibrary(sampleName)
    	assert(actual.equals(expected))
    }
    
    @Test
    def testGetFlowcellId() {    	
    	val expected: String = flowcellId
        val actual: String = illuminaXMLReportReader.getFlowcellId()
    	assert(actual.equals(expected))
    }
    
    @Test
    def testGetPlatformUnitID() {    	
    	val expected: String = flowcellId + "."+ sampleName + "." + lane 
        val actual: String = illuminaXMLReportReader.getPlatformUnitID(sampleName, lane)            	
        assert(actual.equals(expected))
    }
    
    @Test
    def testGetReadGroupID() {
    	val expected: String = flowcellId + "." + sampleName + "." + lane
        val actual: String = illuminaXMLReportReader.getReadGroupID(sampleName, lane)
    	assert(actual.equals(expected))
    }
}