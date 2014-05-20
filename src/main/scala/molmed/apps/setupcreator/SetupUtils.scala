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

  def createProject(): Project = {
    val project = new Project
    project
  }

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

  def setReports(project: Project)(runFolderPaths: Seq[File]): (Project, Seq[Runfolder]) = {

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

    (project, runFolderList)
  }

  def setSamplesAndReference(project: Project)(reference: File): Project = {

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

      val sampleFolderInstances = sampleFolders.map(sampleFolder => {
        val sample = new Samplefolder
        sample.setName(sampleFolder.getName().replace("Sample_", ""))
        sample.setPath(sampleFolder.getAbsolutePath())
        sample.setReference(reference.getAbsolutePath())
        sample
      })

      sampleFolderList.addAll(sampleFolderInstances)
    })

    project
  }

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