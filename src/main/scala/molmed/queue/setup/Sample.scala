package molmed.queue.setup

import java.io.File

case class Sample(sampleName: String, val reference: File, val readGroupInformation: ReadGroupInformation, val readPairContainer: ReadPairContainer) extends SampleAPI {

    def getFastqs(): ReadPairContainer = readPairContainer
    
    def getBwaStyleReadGroupInformationString(): String = {
        readGroupInformation.parseToBwaApprovedString()
    }
    
    def getTophatStyleReadGroupInformationString(): String = {
        readGroupInformation.parseToTophatApprovedString()
    }
    
    def getReadGroupInformation = readGroupInformation
    
    def getReference(): File = reference
    def getSampleName(): String = sampleName

}