package molmed.queue.setup

/**
 * API methods that needs to be implemented by any report reader in order
 * to create a Sample (molmed.queue.setup.Sample).
 */
trait ReportReaderAPI {

  /**
   * @param	sampleName	sample name matching sample in report
   * @param lane		the lane where the library was sequenced
   * @return the library name of the matching sample.
   */
  def getReadLibrary(sampleName: String, lane: Int): String

  /**
   * @return the flowcell id of the flowcell that the report is
   * associated with.
   */
  def getFlowcellId(): String

  /**
   * @param	sampleName	sample names to match
   * @return All lanes where a sample matching the sample name has been sequenced.
   */
  def getLanes(sampleName: String): List[Int]

  /**
   * @param	sampleName	sample name matching sample in report
   * @param lane		the lane where the library was sequenced
   * @return A guaranteed unique ID for the read group.
   */
  def getReadGroupID(sampleName: String, lane: Int): String = {
    getPlatformUnitID(sampleName, lane)
  }

  /**
   * @param	sampleName	sample name matching sample in report
   * @param lane		the lane where the library was sequenced
   * @return The (unique) platform id for the sample
   */
  def getPlatformUnitID(sampleName: String, lane: Int): String =
    getFlowcellId + "." + sampleName + "." + lane

  /**
   * @param sampleName	sample name matching sample in report
   * @param lane		the lane where the library was sequenced
   * @return If possible the number of reads passed filter. Else throws a
   * NotImplementedException.
   */
  def getNumberOfReadsPassedFilter(sampleName: String, lane: Int): Option[Int]

}