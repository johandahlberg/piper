package molmed.queue.setup

trait IlluminaXMLReportReaderAPI {
    def getReadLibrary(sampleName: String, lane: Int): String
    def getFlowcellId(): String
    def getPlatformUnitID(sampleName: String, lane: Int): String
    def getReadGroupID(sampleName: String, lane: Int): String    
    def getLanes(sampleName: String): List[Int]  
}