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
import scala.io.Source

object InputParser {
    implicit def packOptionalValue[T](value: T): Option[T] = Some(value)

    def getMultipleInputs(key: Option[String]): List[String] = {

        def checkInput(value: List[String], checkInputQuestion: Option[String]): List[String] = {
            val valid = readLine(checkInputQuestion.get + "\n")
            valid match {
                case "y" => value
                case "n" => getMultipleInputs(key)
                case _ => {
                    println("Did not recognize input: " + valid)
                    checkInput(value, checkInputQuestion)
                }
            }
        }

        def continue(value: String, accumulator: List[String]): List[String] = {
            val cont = readLine("Do you want to add another " + key.get + "? [y/n]" + "\n")
            cont match {
                case "n" => value :: accumulator
                case "y" => accumulateInputs(key, value :: accumulator)
                case _ => {
                    println("Did not recognize input: " + cont)
                    continue(value, accumulator)
                }
            }
        }

        def accumulateInputs(key: Option[String], acc: List[String]): List[String] = {
            val value = readLine("Set " + key.get + ":" + "\n")            
            checkInput(List(value), "Value of key: " + key.get + ", was set to: " + value + ". Do you want to keep it? [y/n]")
            continue(value, acc)
        }
        
        accumulateInputs(key, List())

    }

    def getSingleInput(key: Option[String], defaultValue: Option[String] = None): String = {

        def checkInput(value: String, checkInputQuestion: Option[String]): String = {
            val valid = readLine(checkInputQuestion.get + "\n")
            valid match {
                case "y" => value
                case "n" => getSingleInput(key)
                case _ => {
                    println("Did not recognize input: " + valid)
                    checkInput(value, checkInputQuestion)
                }
            }
        }

        if (defaultValue.isDefined) {
            checkInput(defaultValue.get, "The default value of " + key.get + " is " + defaultValue.get + ". Do you want to keep it? [y/n]")
        } else {
            val value = readLine("Set " + key.get + ":" + "\n")
            checkInput(value, "Value of key: " + key.get + ", was set to: " + value + ". Do you want to keep it? [y/n]")
        }
    }
}

object SetupFileCreator extends App {

    // Contains the get input method and String => Option[String] conversion.
    import InputParser._

    // Create file and write to it       
    val context = JAXBContext.newInstance(classOf[Project])

    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val project = new Project

    // -------------------------------------------------
    // Create meta data
    // -------------------------------------------------

    val projectMetaData = new Metadata()

    val projectName = getSingleInput("Project name")
    val seqencingPlatform = getSingleInput("Sequencing platform", defaultValue = Some("Illumina"))
    val sequencingCenter = getSingleInput("Sequencing center", defaultValue = Some("SNP_SEQ_PLATFORM"))
    val uppmaxProjectId = getSingleInput("Uppmax project Id", defaultValue = Some("a2009002"))

    projectMetaData.setName(projectName)
    projectMetaData.setPlatfrom(seqencingPlatform)
    projectMetaData.setSequenceingcenter(sequencingCenter)
    projectMetaData.setUppmaxprojectid(uppmaxProjectId)
    project.setMetadata(projectMetaData)

    project.setInputs(new Inputs)

    // -------------------------------------------------
    // Setup run folders
    // -------------------------------------------------

    val runFolderList = project.getInputs().getRunfolder()

    //TODO Fix from here!
    val runFolderPathList = getMultipleInputs(Option("Run folders"))

    runFolderPathList.foreach(println _)

    val runFolder1 = new Runfolder
    runFolder1.setReport("src/test/resources/testdata/runFoldersForMultipleSample/runfolder1/report.xml")

    val runFolder2 = new Runfolder
    runFolder2.setReport("src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/report.xml")

    runFolderList.add(runFolder1)
    runFolderList.add(runFolder2)

    /**
     * Sample folder stuff
     */

    val sampleFolderList1 = runFolder1.getSamplefolder()

    val sampleFolder1 = new Samplefolder()
    sampleFolder1.setName("1")
    sampleFolder1.setPath("src/test/resources/testdata/runFoldersForMultipleSample/runfolder1/Sample_1")
    sampleFolder1.setReference("src/test/resources/testdata/exampleFASTA.fasta")

    sampleFolderList1.add(sampleFolder1)

    val sampleFolderList2 = runFolder2.getSamplefolder()

    val sampleFolder2 = new Samplefolder()
    sampleFolder2.setName("1")
    sampleFolder2.setPath("src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/Sample_1")
    sampleFolder2.setReference("src/test/resources/testdata/exampleFASTA.fasta")

    sampleFolderList2.add(sampleFolder2)

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
    //marshaller.marshal(project, new FileOutputStream("src/test/resources/testdata/newPipelineSetupSameSampleAcrossMultipleRunFolders.xml"))

}