package molmed.queue.setup.stubs

import molmed.queue.setup.SampleAPI
import molmed.queue.setup.ReadPairContainer
import java.io.File
import molmed.queue.setup.ReadGroupInformation
import org.apache.commons.lang.NotImplementedException

class SampleStub(sampleName: String) extends SampleAPI{
    
    var readPairContainer: ReadPairContainer = null
    var bwaReadGroupInfo: String = ""
    var tophatReadgroupInfo: String = ""
    var reference: File = null    
    
    def getSampleName(): String = sampleName
    def getFastqs(): ReadPairContainer = readPairContainer   
    def getBwaStyleReadGroupInformationString: String = bwaReadGroupInfo
    def getTophatStyleReadGroupInformationString(): String = tophatReadgroupInfo
    def getReference: File = reference
    def getReadGroupInformation(): ReadGroupInformation = throw new NotImplementedException()
    
    
    override
    def hashCode(): Int = {
        sampleName.hashCode()
    }
    
}