package molmed.queue.setup

import javax.xml.bind.JAXBContext
import molmed.xml.setup.Samplefolder
import molmed.xml.setup.Project
import molmed.xml.setup.Qscript
import molmed.xml.setup.Runfolder
import molmed.xml.setup.Metadata
import molmed.xml.setup.Argument
import molmed.xml.setup.Inputs
import java.io.FileOutputStream
import molmed.xml.setup.Analysis
import javax.xml.bind.Marshaller

object SetupFileCreator extends App {
    
      // Create file and write to it       
    val context = JAXBContext.newInstance(classOf[Project])

    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val project = new Project

    val projectMetaData = new Metadata()
    projectMetaData.setName("Test project")
    projectMetaData.setPlatfrom("Illumina")
    projectMetaData.setSequenceingcenter("SNP_SEQ_PLATFORM")
    projectMetaData.setUppmaxprojectid("a2009002")
    project.setMetadata(projectMetaData)

    project.setInputs(new Inputs)

    /**
     * Run folder stuff
     */

    val runFolderList = project.getInputs().getRunfolder()

    val runFolder = new Runfolder
    runFolder.setReport("src/test/resources/testdata/smallTestFastqDataFolder/report.xml")

    runFolderList.add(runFolder)

    /**
     * Sample folder stuff
     */

    val sampleFolderList = runFolder.getSamplefolder()

    val sampleFolder1 = new Samplefolder()
    sampleFolder1.setName("1")
    sampleFolder1.setPath("src/test/resources/testdata/smallTestFastqDataFolder/Sample_1")
    sampleFolder1.setReference("src/test/resources/testdata/exampleFASTA.fasta")

    val sampleFolder2 = new Samplefolder()
    sampleFolder2.setName("2")
    sampleFolder2.setPath("src/test/resources/testdata/smallTestFastqDataFolder/Sample_2")
    sampleFolder2.setReference("src/test/resources/testdata/exampleFASTA.fasta")

    sampleFolderList.add(sampleFolder1)
    sampleFolderList.add(sampleFolder2)

    /**
     * Analysis stuff
     */

    project.setAnalysis(new Analysis)

    val analysis = project.getAnalysis()
    val qscriptList = analysis.getQscript()

    val qscript1 = new Qscript
    qscript1.setName("HelloWorld")
    qscript1.setPath("examplePath/helloWorld.scala")
    val argList = qscript1.getArgument()

    val argument = new Argument
    argument.setKey("MyFirstKey")
    argument.setValue("MyFirstValue")
    argList.add(argument)

    val argument2 = new Argument
    argument2.setKey("MySecondKey")
    argument2.setValue("MySecondValue")
    argList.add(argument2)

    qscriptList.add(qscript1)

    //marshaller.marshal(project, System.out)
    marshaller.marshal(project, new FileOutputStream("src/test/resources/testdata/exampleForNewSetupXML.xml"))

}