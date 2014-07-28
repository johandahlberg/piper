package molmed.apps.setupcreator

import java.io.File
import java.io.FileOutputStream

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList

import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import molmed.xml.setup.Inputs
import molmed.xml.setup.Metadata
import molmed.xml.setup.Project
import molmed.xml.setup.Runfolder
import molmed.xml.setup.Samplefolder

object SetupUtils {

  /**
   * Create a XML-serializable project instance
   */
  def createProject(): Project = {
    val project = new Project
    project
  }

  /**
   * Set the meta data for a project
   * @param project	The project to a apply the meta data to.
   * @param projectName
   * @param	seqencingPlatform
   * @param sequencingCenter
   * @param uppmaxProjectId
   * @param uppmaxQoSFlag
   * @return The project modified with the meta data.
   */
  def setMetaData(project: Project)(projectName: String,
                                    seqencingPlatform: String,
                                    sequencingCenter: String,
                                    uppmaxProjectId: String,
                                    uppmaxQoSFlag: String): Project = {
    val projectMetaData = new Metadata()

    projectMetaData.setName(projectName)
    projectMetaData.setPlatfrom(seqencingPlatform)
    projectMetaData.setSequenceingcenter(sequencingCenter)
    projectMetaData.setUppmaxprojectid(uppmaxProjectId)
    projectMetaData.setUppmaxqos(uppmaxQoSFlag)
    project.setMetadata(projectMetaData)

    project.setInputs(new Inputs)
    project
  }

  /**
   * From a Seq of sample paths, this will create a project structure including
   * all the samples referred to.
   * @param project		the project to add the samples to
   * @param samplePaths	paths to all sample directoris the you which to use.
   * @param reference	the reference to add.
   * @return
   */
  def setupRunfolderStructureFromSamplePaths(project: Project)(samplePaths: Set[File], reference: File): Project = {

    require(samplePaths.forall(p => p.isDirectory()),
      "You supplied file instead of a directory to the sample path")
    require(samplePaths.forall(p => p.getName().startsWith("Sample_")),
      "Are you sure that you provided a sample folder as input." +
        " The name of the sample folder must start with Sample_<the rest of" +
        "the name>.")
    require(reference.exists(), "Reference " + reference.getAbsolutePath() + " supplied did not exist.")

    val modifiedProject = samplePaths.
      foldLeft(project)((project, sampleDir) => {
        val runFolderDir = sampleDir.getParentFile()
        val projectWithRunfolder = setRunfolders(project)(Seq(runFolderDir))

        val runFolderList = projectWithRunfolder.getInputs().getRunfolder()

        def runfolderParentFile(runfolder: Runfolder) =
          new File(runfolder.getReport()).getParentFile()

        for {
          runfolder <- runFolderList
          if runfolderParentFile(runfolder) == sampleDir.getParentFile()
        } {
          val sampleFolder = runfolder.getSamplefolder()
          // This is now done as a side effect - which is not pretty, but
          // hopefully it will work.
          addSampleFolder(sampleFolder, Seq(sampleDir), reference)
        }

        projectWithRunfolder
      })

    modifiedProject
  }

  /**
   * Will look for a matching report file (with a tsv or xml) file ending in
   * the directories provided by runFolderPaths.
   *
   * @param project			The project to add the runfolders to.
   * @param runFolderPaths	Paths to the run folders to add
   * @return the project with runfolders set.
   */
  def setRunfolders(project: Project)(runFolderPaths: Seq[File]): Project = {

    val runFolderList = project.getInputs().getRunfolder()

    runFolderList.addAll(runFolderPaths.map(path => {

      def lookForReport(dir: File): String = {

        require(dir.isDirectory(), dir + " was not a directory.")

        val reportFile: File = dir.listFiles().
          find(report => report.getName() == "report.xml" || report.getName() == "report.tsv").
          getOrElse(throw new Error("Could not find report.xml in " + dir.getPath()))

        reportFile.getAbsolutePath()
      }

      val runFolder = new Runfolder()
      runFolder.setReport(lookForReport(path))
      runFolder

    }))

    project
  }

  private def addSampleFolder(sampleFolderList: Seq[Samplefolder], sampleFolders: Seq[File], reference: File) = {

    val sampleFolderInstances = sampleFolders.map(sampleFolder => {
      val sample = new Samplefolder
      sample.setName(sampleFolder.getName().replace("Sample_", ""))
      sample.setPath(sampleFolder.getAbsolutePath())
      sample.setReference(reference.getAbsolutePath())
      sample
    })

    sampleFolderList.addAll(sampleFolderInstances)
  }

  /**
   * Setup the samples based on the runfolders already added to the project
   * by the setReports. This will look in the directories generated which
   * contain the report files and get all samples from those.
   * @param	project		project to add the reference to
   * @param reference	reference to add.
   * @return the project with all samples set up.
   */
  def setReferenceForSamples(project: Project)(reference: File): Project = {

    val runFolderList = project.getInputs().getRunfolder()

    runFolderList.map(runFolder => {

      val sampleFolderList = runFolder.getSamplefolder()
      val runFolderPath = (new File(runFolder.getReport)).getParentFile()

      val sampleFolders =
        runFolderPath.
          listFiles().
          filter(s => s.getName().
            startsWith("Sample_")).
          toList

      addSampleFolder(sampleFolderList, sampleFolders, reference)
    })

    project
  }

  /**
   * Writes the project to a specified xml file.
   * @param project		The project to write
   * @param outputFile	The file to write to.
   */
  def writeToFile(project: Project, outputFile: File) = {
    // The xml marshaller is used to create the xml instance
    val context = JAXBContext.newInstance(classOf[Project])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val os = new FileOutputStream(outputFile)
    marshaller.marshal(project, os)
    os.close()

  }

}