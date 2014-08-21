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

object SetupXMLReader {

  /**
   * A factory method to create setup file readers of different types
   * It will try to detect the file format and return a correct reader
   * for the format of the input file.
   * 
   * @param setupXML the setup xml which to read
   */
  def apply(setupXML: File): SetupXMLReaderAPI =
    new LegacySetupXMLReader(setupXML)
}
    
