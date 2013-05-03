package molmed.queue.setup

import java.io.File

case class Sample(sampleName: String, reference: File, readGroupInformation: ReadGroupInformation, readPairContainer: ReadPairContainer) extends SampleAPI {

    def getFastqs(): ReadPairContainer = readPairContainer
    
    def getBwaStyleReadGroupInformationString(): String = {
        readGroupInformation.parseToBwaApprovedString()
    }
    
    def getTophatStyleReadGroupInformationString(): String = {
        readGroupInformation.parseToTophatApprovedString()
    }
    
    def getReference(): File = reference
    def getSampleName(): String = sampleName

}