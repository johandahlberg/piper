package molmed.queue.setup

import java.io.File

/**
 * Container class representing a sample, the reference it is aligned to,
 * and the read pairs which from the sequencing machine and the read group
 * information associated with it.
 */
case class Sample(sampleName: String,
                  val reference: File,
                  val readGroupInformation: ReadGroupInformation,
                  val readPairContainer: ReadPairContainer)
    extends SampleAPI {

  def getFastqs(): ReadPairContainer = readPairContainer

  def getBwaStyleReadGroupInformationString(): String = {
    readGroupInformation.parseToBwaApprovedString()
  }

  def getTophatStyleReadGroupInformationString(): String = {
    readGroupInformation.parseToTophatApprovedString()
  }

  def getReadGroupInformation = readGroupInformation

  def getReference(): File = reference
  def getSampleName(): String = sampleName

}