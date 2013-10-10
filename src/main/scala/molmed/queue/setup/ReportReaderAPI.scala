package molmed.queue.setup

trait ReportReaderAPI {
  def getReadLibrary(sampleName: String, lane: Int): String
  def getFlowcellId(): String
  def getLanes(sampleName: String): List[Int]

  def getReadGroupID(sampleName: String, lane: Int): String = {
    getPlatformUnitID(sampleName, lane)
  }

  def getPlatformUnitID(sampleName: String, lane: Int): String =
    getFlowcellId + "." + sampleName + "." + lane

}