package molmed.queue.setup.stubs
import molmed.queue.setup.SampleAPI
import molmed.queue.setup.ReadPairContainer
import java.io.File

class SampleStub(sampleName: String) extends SampleAPI{
    
    var readPairContainer: ReadPairContainer = null
    var readGroupInfo: String = ""
    var reference: File = null    
    
    def getSampleName(): String = sampleName
    def getFastqs(): ReadPairContainer = readPairContainer   
    def getReadGroupInformation: String = readGroupInfo
    def getReference: File = reference
    
    override
    def hashCode(): Int = {
        sampleName.hashCode()
    }
    
}