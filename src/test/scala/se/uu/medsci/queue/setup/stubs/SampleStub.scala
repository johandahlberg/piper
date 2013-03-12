package se.uu.medsci.queue.setup.stubs
import se.uu.medsci.queue.setup.SampleAPI
import se.uu.medsci.queue.setup.ReadPairContainer
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