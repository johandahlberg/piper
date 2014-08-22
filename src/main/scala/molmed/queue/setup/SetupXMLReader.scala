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

class SetupXMLReader(setupXML: File) extends SetupXMLReaderAPI {

  def getPlatform(): String = ???
  def getProjectName(): Option[String] = ???
  def getReference(sampleName: String): java.io.File = ???
  def getSamples(): Map[String, Seq[molmed.queue.setup.SampleAPI]] = ???
  def getSequencingCenter(): String = ???
  def getUppmaxProjectId(): String = ???
  def getUppmaxQoSFlag(): Option[String] = ???

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
        lines.exists(s => s == "<project xmlns=\"legacy.setup.xml.molmed\">")
      source.close()
      result
    }

    if (isLegacyXMLFile(setupXML))
      new LegacySetupXMLReader(setupXML)
    else
      new SetupXMLReader(setupXML)

  }

}
    
