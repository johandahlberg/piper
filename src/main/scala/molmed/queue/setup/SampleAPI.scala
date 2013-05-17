package molmed.queue.setup

import java.io.File

trait SampleAPI {
    def getFastqs(): ReadPairContainer
    def getReadGroupInformation(): ReadGroupInformation
    def getBwaStyleReadGroupInformationString(): String
    def getTophatStyleReadGroupInformationString(): String
    def getReference(): File
    def getSampleName(): String
}
