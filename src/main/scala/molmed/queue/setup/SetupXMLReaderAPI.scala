package molmed.queue.setup

import java.io.File

trait SetupXMLReaderAPI {   
    def getSampleFolder(sampleName: String, runFolderName: String): File    
    def getPlatform(): String
    def getSequencingCenter(): String   
    def getProjectName(): String
    def getSamples(): Map[String, Seq[SampleAPI]]
    def getReference(sampleName: String): File
    def getUppmaxProjectId(): String
}