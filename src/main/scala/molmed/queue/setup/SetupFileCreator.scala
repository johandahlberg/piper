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
import java.io.File
import collection.JavaConversions._

object ConsoleInputParser {
    implicit def packOptionalValue[T](value: T): Option[T] = Some(value)

    private def checkInput[T](function: Option[String] => T)(key: Option[String], value: T, checkInputQuestion: Option[String]): T = {
        val valid = readLine(checkInputQuestion.get + "\n")
        valid match {
            case "y" => value
            case "n" => function(key)
            case _ => {
                println("Did not recognize input: " + valid)
                checkInput(function)(key, value, checkInputQuestion)
            }
        }
    }

    def getMultipleInputs(key: Option[String]): List[String] = {

        def continue(accumulator: List[String]): List[String] = {

            val value = readLine("Set " + key.get + ":" + "\n")
            checkInput[List[String]](getMultipleInputs)(key, List(value), "Value of key: " + key.get + ", was set to: " + value + ". Do you want to keep it? [y/n]")

            val cont = readLine("Do you want to add another " + key.get + "? [y/n]" + "\n")
            cont match {
                case "n" => value :: accumulator
                case "y" => continue(value :: accumulator)
                case _ => {
                    println("Did not recognize input: " + cont)
                    continue(accumulator)
                }
            }
        }

        continue(List())
    }

    def withDefaultValue[T](key: Option[String], defaultValue: T)(function: Option[String] => T): T = {
        if (defaultValue.isDefined)
            checkInput(function)(key, defaultValue.get, "The default value of " + key.get + " is " + defaultValue.get + ". Do you want to keep it? [y/n]")
        else
            function(key)
    }

    def getSingleInput(key: Option[String]): String = {
        val value = readLine("Set " + key.get + ":" + "\n")
        checkInput[String](getSingleInput)(key, value, "Value of key: " + key.get + ", was set to: " + value + ". Do you want to keep it? [y/n]")
    }
}

object SetupFileCreator extends App {

    // Simple output file creation
    val outputFile = try {
        new File(args(0))
    } catch {
        case e: ArrayIndexOutOfBoundsException => throw new IllegalArgumentException("Missing output file. Usage is: java -classpath <classpath> molmed.queue.setup.SetupFileCreator <output xml file>")
    }

    // Contains the get input method and String => Option[String] conversion.
    import ConsoleInputParser._

    // The xml marshaller is used to create the xml instance
    val context = JAXBContext.newInstance(classOf[Project])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val project = new Project

    // -------------------------------------------------
    // Create meta data
    // -------------------------------------------------

    val projectMetaData = new Metadata()

    val projectName = getSingleInput("Project name")
    val seqencingPlatform = withDefaultValue("Sequencing platform", defaultValue = "Illumina")(getSingleInput)
    val sequencingCenter = withDefaultValue("Sequencing center", defaultValue = "UU-SNP")(getSingleInput)
    val uppmaxProjectId = withDefaultValue("Uppmax project id", defaultValue = "a2009002")(getSingleInput)
    val uppmaxQoSFlag = withDefaultValue("Uppmax QoS flag (default is no flag)", defaultValue = "")(getSingleInput)

    projectMetaData.setName(projectName)
    projectMetaData.setPlatfrom(seqencingPlatform)
    projectMetaData.setSequenceingcenter(sequencingCenter)
    projectMetaData.setUppmaxprojectid(uppmaxProjectId)
    projectMetaData.setUppmaxqos(uppmaxQoSFlag)
    project.setMetadata(projectMetaData)    

    project.setInputs(new Inputs)

    def getReference: String = {
        val reference = new File(withDefaultValue("Reference", 
            defaultValue = "/proj/b2010028/references/piper_references/gatk_bundle/2.2/b37/human_g1k_v37.fasta")
            (getSingleInput)).getAbsolutePath()

        try {
            require(new File(reference).exists(), "Cannot find reference: " + reference)
        } catch {
            case ie: IllegalArgumentException => {
                println("Cannot find reference: " + reference + ". Input a correct reference.")
                getReference
            }

        }
        reference
    }

    val reference = getReference

    // -------------------------------------------------
    // Setup run folders
    // -------------------------------------------------
    
    def getRunFoldersFromRootDir(): List[String] = {
        val rootDir = new File(getSingleInput(Option("Path to the run folder root dir")))
        require(rootDir.isDirectory(), rootDir + " was not a directory.")
        rootDir.listFiles().toList.map(f => f.getAbsolutePath())
    }
    
    
    val runFolderList = project.getInputs().getRunfolder()    

    val runFolderPathList = getRunFoldersFromRootDir()

    runFolderList.addAll(runFolderPathList.map(path => {

        def lookForReport(p: String): String = {
            val dir = new File(p)
            require(dir.isDirectory(), dir + " was not a directory.")
            val reportFile: File = dir.listFiles().find(report => report.getName() == "report.xml" || report.getName() == "report.tsv").getOrElse(throw new Error("Could not find report.xml in " + dir.getPath()))
            reportFile.getAbsolutePath()
        }

        val runFolder = new Runfolder
        runFolder.setReport(lookForReport(path))
        runFolder

    }))

    // -------------------------------------------------
    // Setup sample folders
    // -------------------------------------------------

    runFolderList.map(runFolder => {
        val sampleFolderList = runFolder.getSamplefolder()
        val runFolderPath = new File(runFolder.getReport.get).getParentFile()
        val sampleFolders = runFolderPath.listFiles().filter(s => s.getName().startsWith("Sample_")).toList

        val sampleFolderInstances = sampleFolders.map(sampleFolder => {
            val sample = new Samplefolder
            sample.setName(sampleFolder.getName().replace("Sample_", ""))
            sample.setPath(sampleFolder.getAbsolutePath())
            sample.setReference(reference)
            sample
        })

        sampleFolderList.addAll(sampleFolderInstances)
    })

    // @TODO
    // Leaving the below here so that it can be used as a template when the qscripts are converted to a xml
    // based argument setup.

    // -------------------------------------------------
    // Analysis stuff
    // -------------------------------------------------

    //    project.setAnalysis(new Analysis)
    //
    //    val analysis = project.getAnalysis()
    //    val qscriptList = analysis.getQscript()
    //
    //    val qscript1 = new Qscript
    //    qscript1.setName("HelloWorld")
    //    qscript1.setPath("examplePath/helloWorld.scala")
    //    val argList = qscript1.getArgument()
    //
    //    val argument = new Argument
    //    argument.setKey("MyFirstKey")
    //    argument.setValue("MyFirstValue")
    //    argList.add(argument)
    //
    //    val argument2 = new Argument
    //    argument2.setKey("MySecondKey")
    //    argument2.setValue("MySecondValue")
    //    argList.add(argument2)
    //
    //    qscriptList.add(qscript1)

    //marshaller.marshal(project, System.out)

    println("Finished setting up project. Writing project xml to " + outputFile.getAbsolutePath() + " now.")
    marshaller.marshal(project, new FileOutputStream(outputFile))

}