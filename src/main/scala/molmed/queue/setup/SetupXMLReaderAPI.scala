package molmed.queue.setup

import java.io.File

/**
 * API for reading setup xmls. This needs to be implemented by setup file
 * reads if you want to use some other format.
 */
trait SetupXMLReaderAPI {
  
  /**
   * @return a sequencing platform, e.g. "Illumina"
   */
  def getPlatform(): String

  /**
   * @return a sequencing center e.g. "Snp and Seq platform, Uppsala"
   */
  def getSequencingCenter(): String

  /**
   * @return the project name e.g. "ExampleProject123"
   */
  def getProjectName(): Option[String]

  /**
   * @return all samples associate with the project as mapped by the sample name
   */
  def getSamples(): Map[String, Seq[SampleAPI]]

  /**
   * @param sampleNam
   * @return the reference that the sample should be aligned to.
   */
  def getReference(sampleName: String): File

  /**
   * @return the uppmax project id, i.e. the project which should be charged for the 
   * hours.
   */
  def getUppmaxProjectId(): String

  /**
   * @return the QoS flag to be sent to the cluster.
   */
  def getUppmaxQoSFlag(): Option[String]
}