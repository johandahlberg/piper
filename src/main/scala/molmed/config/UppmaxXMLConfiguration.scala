package molmed.config

import molmed.queue.setup.SetupXMLReaderAPI
import molmed.queue.setup.SetupXMLReader
import java.io.File
import molmed.utils.UppmaxConfig
import molmed.utils.Uppmaxable

/**
 * Trait used with QScripts to load a setup xml file.
 * Provided function to load all uppmax required
 * configurations.
 */
trait UppmaxXMLConfiguration extends Uppmaxable {

  @Input(doc = "input pipeline setup xml", fullName = "xml_input", shortName = "xi", required = true)
  var setupXML: File = _

  var setupReader: SetupXMLReaderAPI = _

  /**
   * Will set all the uppmax parameters in the Uppmaxable trait.
   *
   * @param setupXML	xml setup file
   * @param testMode	run in test mode
   * @return a container with the uppmax config.
   */
  def loadUppmaxConfigFromXML(setupXML: File = this.setupXML, testMode: Boolean = false): UppmaxConfig = {

    setupReader = new SetupXMLReader(setupXML)
    projId = setupReader.getUppmaxProjectId()
    uppmaxQoSFlag = setupReader.getUppmaxQoSFlag()
    projectName = setupReader.getProjectName()

    UppmaxConfig(projId, uppmaxQoSFlag, testMode = testMode)
  }

}