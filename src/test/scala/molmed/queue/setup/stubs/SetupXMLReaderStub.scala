package molmed.queue.setup.stubs

import molmed.queue.setup.SetupXMLReaderAPI
import java.io.File
import molmed.queue.setup.SampleAPI

class SetupXMLReaderStub extends SetupXMLReaderAPI{
    
    var sampleFolder: File = null
    var platform: String = null
    var sequencingCenter: String = null
    var projectName: String = null
    var samples:  Map[String, Seq[SampleAPI]] = null
    var reference: File = null
    var uppmaxProjectId = null
    
    def getSampleFolder(sampleName: String, runFolderName: String): File  = {sampleFolder}  
    def getPlatform(): String = {platform}
    def getSequencingCenter(): String   = {sequencingCenter}
    def getProjectName(): String = {projectName}
    def getSamples():  Map[String, Seq[SampleAPI]] = {samples} 
    def getReference(sampleName: String): File = {reference}
    def getUppmaxProjectId() = {uppmaxProjectId}    
    
}