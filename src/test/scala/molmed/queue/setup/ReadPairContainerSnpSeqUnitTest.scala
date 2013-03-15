package molmed.queue.setup

import org.testng.annotations.Test
import org.testng.Assert
import java.io.File
import molmed.queue.SnpSeqBaseTest

class ReadPairContainerSnpSeqUnitTest{
    
    val baseTest = new SnpSeqBaseTest()
    
    @Test
    def testIsMatePaired() {    
        
        val mate1: File = new File(baseTest.pathToMate1)      
        val mate2: File = new File(baseTest.pathToMate1)
        val sampleName: String = "testSample"
                      
        // Class under test
        val readPairContainer: ReadPairContainer = new ReadPairContainer(mate1, mate2, sampleName) 
        
        // Run the test
        assert(readPairContainer.isMatePaired())
    }
    
    @Test
    def testIsNotMatePaired() {
    	
        val mate1: File = new File(baseTest.pathToMate1)
        val mate2: File = null
        val sampleName: String = "testSample"
                      
        // Class under test
        val readPairContainer: ReadPairContainer = new ReadPairContainer(mate1, mate2, sampleName) 
        
        // Run the test
        assert(!readPairContainer.isMatePaired())
    }

}