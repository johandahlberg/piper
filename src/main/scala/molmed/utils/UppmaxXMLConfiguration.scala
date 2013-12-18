package molmed.utils

import molmed.queue.setup.SetupXMLReaderAPI
import molmed.queue.setup.SetupXMLReader
import java.io.File
import org.broadinstitute.sting.commandline.Input

trait UppmaxXMLConfiguration extends Uppmaxable {

  @Input(doc = "input pipeline setup xml", fullName = "xml_input", shortName = "xi", required = true)
  var setupXML: File = _

  var setupReader: SetupXMLReaderAPI = _

  // Load the setup
  def loadUppmaxConfigFromXML(setupXML: File = this.setupXML, testMode: Boolean = false): UppmaxConfig = {

    setupReader = new SetupXMLReader(setupXML)
    projId = setupReader.getUppmaxProjectId()
    uppmaxQoSFlag = setupReader.getUppmaxQoSFlag()
    projectName = setupReader.getProjectName()

    UppmaxConfig(projId, uppmaxQoSFlag, testMode = testMode)
  }

}