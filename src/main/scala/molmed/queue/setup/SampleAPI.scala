package molmed.queue.setup

import java.io.File

/**
 * API for samples. Should be implemented by any class which provides a concrete implementation of
 * the sample concept. Each sample should be unique as determined by the read group information
 * readGroupId.
 */
trait SampleAPI {
  /**
   * @return the read pair container associated with the sample.
   */
  def getFastqs(): ReadPairContainer

  /**
   * @return the read group information associated with the sample.
   */
  def getReadGroupInformation(): ReadGroupInformation

  /**
   * @return the read group information as presented to bwa
   */
  def getBwaStyleReadGroupInformationString(): String
  
  /**
   * @return the read group information as presented to tophat
   */
  def getTophatStyleReadGroupInformationString(): String
  
  /**
   * @return the reference to align to.
   */
  def getReference(): File
  
  /**
   * @return the name of the sample.
   */
  def getSampleName(): String
}
