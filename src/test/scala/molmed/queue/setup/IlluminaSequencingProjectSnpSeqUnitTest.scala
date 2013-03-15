package molmed.queue.setup

import org.testng.annotations.Test
import org.testng.Assert
import molmed.queue.setup.stubs._

class IlluminaSequencingProjectSnpSeqUnitTest {

    @Test
    def testGetSamples() {
    	
        // Setup
        val setupXMLReaderStub: SetupXMLReaderStub = new SetupXMLReaderStub()
        
        val expected:  scala.collection.mutable.Map[String, Seq[SampleAPI]] = scala.collection.mutable.Map.empty[String, Seq[SampleAPI]]
	    expected("1") = Seq(new SampleStub("1"))
	    expected("2") = Seq(new SampleStub("2"))
	    expected("3") = Seq(new SampleStub("3"))

	    setupXMLReaderStub.samples = expected.toMap
                      
        // Class under test
        val illuminaSequencingProject: IlluminaSequencingProject = new IlluminaSequencingProject(setupXMLReaderStub)
        
        // Run the test
        val actual: Map[String, Seq[SampleAPI]] = illuminaSequencingProject.getSamples()
        assert(actual.sameElements(expected.toMap))
    }
    
    
}