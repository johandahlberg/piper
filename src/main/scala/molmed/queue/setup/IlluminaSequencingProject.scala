package molmed.queue.setup

import org.apache.commons.lang.NotImplementedException
import scala.collection.Seq
import collection.JavaConversions._

class IlluminaSequencingProject(setupXml: SetupXMLReaderAPI) {
    
    def getSamples(): Map[String, Seq[SampleAPI]] = {setupXml.getSamples()}

}