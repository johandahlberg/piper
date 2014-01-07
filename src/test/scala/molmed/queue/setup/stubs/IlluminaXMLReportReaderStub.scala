package molmed.queue.setup.stubs

import molmed.queue.setup.ReportReaderAPI

class IlluminaXMLReportReaderStub extends ReportReaderAPI{
    
    var sampleName: String = null
    var flowCellId: String = null
    var platformUnitId: String = null
    var readGroupId: String = null
    var readLibrary: String = null
    var lanes: List[Int] = null
    var readsPassfilter: Option[Int] = None
    
    
    def getReadLibrary(sampleName: String, lane: Int): String = readLibrary
    def getFlowcellId(): String = flowCellId
    override def getPlatformUnitID(sampleName: String, lane:Int): String = platformUnitId
    override def getReadGroupID(sampleName: String, lane: Int): String  = readGroupId
    def getLanes(sampleName: String): List[Int] = lanes
    def getNumberOfReadsPassedFilter(sampleName: String, lane: Int): Option[Int] = readsPassfilter

}