package molmed.queue.setup

import java.io.File

trait SetupXMLReaderAPI {
    def getSampleFolder(sampleName: String, runFolderName: String): File

    /**
     * Return platform
     * 
     * For example: "Illumina"
     */
    def getPlatform(): String

    /**
     * Return the sequencing center
     * 
     * For example: "Snp and Seq platform, Uppsala"
     */
    def getSequencingCenter(): String

    /**
     * Return the project name 
     * 
     * For example: "ExampleProject123"
     */
    def getProjectName(): Option[String]

    def getSamples(): Map[String, Seq[SampleAPI]]

    def getReference(sampleName: String): File

    def getUppmaxProjectId(): String
    
    def getUppmaxQoSFlag(): Option[String]
}