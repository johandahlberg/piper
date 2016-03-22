package molmed.queue.setup

import java.io.File
import molmed.xml.illuminareport.SequencingReport
import java.io.StringReader
import javax.xml.bind.JAXBContext
import scala.collection.mutable.Buffer
import molmed.xml.illuminareport.Read
import javax.xml.bind.Marshaller
import collection.JavaConversions._
import java.io.FileNotFoundException
import molmed.utils.GeneralUtils
import scala.io.Source
import molmed.xml.setup.Project

class SetupXMLReader(setupXML: File) extends SetupXMLReaderAPI {

  // XML related fields         
  private val context = JAXBContext.newInstance(classOf[Project])
  private val unmarshaller = context.createUnmarshaller()
  private val setupReader =
    new StringReader(scala.io.Source.fromFile(setupXML).mkString)
  private val project =
    unmarshaller.unmarshal(setupReader).asInstanceOf[Project]

  def getPlatform(): String =
    project.getMetadata().getPlatform()

  def getProjectName(): Option[String] = {
    val projectName = project.getMetadata().getName()
    if (projectName.isEmpty())
      None
    else
      Some(projectName)
  }

  /**
   * NOTE: this ignores the sampleName!
   * @TODO In the future this should be removed. The same reference is
   * assumed for all samples in a project.
   */
  def getReference(sampleName: String): java.io.File =
    new File(project.getMetadata().getReference()).getAbsoluteFile()

  def getSamples(): Map[String, Seq[molmed.queue.setup.SampleAPI]] = {

    val sampleList =
      for {
        sample <- this.project.getInputs().getSample()
        library <- sample.getLibrary()
        platformUnit <- library.getPlatformunit()
      } yield {

        val fastqsFiles = platformUnit.getFastqfile()

        val sampleName = sample.getSamplename()
        val reference = this.getReference(sampleName)
        val platformUnitId = platformUnit.getUnitinfo()
        val readGroupId = platformUnitId
        val sequencingCenter = this.getSequencingCenter()
        val platform = this.getPlatform()
        val readLibrary = library.getLibraryname()

        val readGroupInformation =
          new ReadGroupInformation(
            sampleName,
            readGroupId,
            sequencingCenter,
            readLibrary,
            platform,
            platformUnitId,
            readsPassFilter = None)

        val readPairContainer = fastqsFiles.size() match {
          case 1 => new ReadPairContainer(new File(fastqsFiles(0).getPath()))
          case 2 => new ReadPairContainer(
            new File(fastqsFiles(0).getPath()),
            new File(fastqsFiles(1).getPath()))
          case _ => throw new Exception("Found unrecognized numer of read pairs!")
        }

        val sampleInstance = new Sample(
          sampleName,
          reference,
          readGroupInformation,
          readPairContainer)

        sampleInstance
      }

    val sampleNameToSampleInstanceMap =
      sampleList.groupBy(f => f.getSampleName).
      mapValues(f => f.toSeq)

    sampleNameToSampleInstanceMap
  }

  def getSequencingCenter(): String =
    project.getMetadata().getSequencingcenter()

  def getUppmaxProjectId(): String =
    project.getMetadata().getUppmaxprojectid()

  def getUppmaxQoSFlag(): Option[String] = {
    val qos = project.getMetadata().getUppmaxqos()
    if (qos.isEmpty())
      None
    else
      Some(qos)
  }

}

object SetupXMLReader {

  /**
   * A factory method to create setup file readers of different types
   * It will try to detect the file format and return a correct reader
   * for the format of the input file.
   *
   * @param setupXML the setup xml which to read
   */
  def apply(setupXML: File): SetupXMLReaderAPI = {

    def isLegacyXMLFile(file: File): Boolean = {
      val source = Source.fromFile(file)
      val lines = source.getLines
      val result =
        lines.exists(s => s.contains( "<runfolder>"))
      source.close()
      result
    }

    if (isLegacyXMLFile(setupXML))
      new LegacySetupXMLReader(setupXML)
    else
      new SetupXMLReader(setupXML)

  }

}
    
