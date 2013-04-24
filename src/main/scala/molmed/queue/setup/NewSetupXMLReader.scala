package molmed.queue.setup

import java.io.File
import molmed.xml.illuminareport.SequencingReport
import java.io.StringReader
import javax.xml.bind.JAXBContext
import scala.collection.mutable.Buffer
import molmed.xml.illuminareport.Read
import javax.xml.bind.Marshaller
import collection.JavaConversions._
import molmed.xml.setup.Project
import molmed.xml.setup.Samplefolder
import java.io.FileNotFoundException

class NewSetupXMLReader(setupXML: File) extends SetupXMLReaderAPI {

    /**
     * XML related fields
     */
    val context = JAXBContext.newInstance(classOf[Project])
    val unmarshaller = context.createUnmarshaller()
    val setupReader = new StringReader(scala.io.Source.fromFile(setupXML).mkString)
    val project = unmarshaller.unmarshal(setupReader).asInstanceOf[Project]

    
    /**
     * Fields containing information on the runfolders/samples etc kept in a convenient form.
     */
    private val sampleList = project.getInputs().getRunfolder().flatMap(f => f.getSamplefolder())
    
    private val runFolderList = project.getInputs().getRunfolder().toList
    
    private val runFolderReportToSampleListMap: Map[String, java.util.List[molmed.xml.setup.Samplefolder]] =
        runFolderList.map(runFolder => {
            (runFolder.getReport(), runFolder.getSamplefolder())
        }).toMap

    // TODO Possibly remove this from API!
    def getSampleFolder(sampleName: String, runFolderName: String): File = new File("Mock file")

    /**
     * Implementations of the API methods
     */
    
    def getPlatform(): String = {
        project.getMetadata().getPlatfrom()
    }

    def getSequencingCenter(): String = {
        project.getMetadata().getSequenceingcenter()
    }

    def getProjectName(): String = {
        project.getMetadata().getName()
    }

    def getSamples(): Map[String, Seq[NewSample]] = {

        /**
         * Helper methods
         */
        def getSampleList(sampleName: String): Seq[NewSample] = {

            def getSamplesFromAllRunFolders(sampleName: String, runFolderToSampleFolderMap: Map[String, java.util.List[molmed.xml.setup.Samplefolder]]): List[NewSample] = {
                runFolderToSampleFolderMap.flatMap(tupple => {

                    val report = tupple._1
                    val sampleFolderList = tupple._2

                    val reportReader = new IlluminaXMLReportReader(new File(report))

                    val sampleList: Seq[NewSample] = sampleFolderList.flatMap(sampleFolder => {

                        val sampleName = sampleFolder.getName()

                        def buildSampleList(sampleName: String): List[NewSample] =
                            reportReader.getLanes(sampleName).map(lane => {
                                val readPairContainer = buildReadPairContainer(sampleFolder, lane)
                                val readGroupInfo = buildReadGroupInformation(sampleName, lane, reportReader)
                                new NewSample(sampleName, getReference(sampleName), readGroupInfo, readPairContainer)
                            })

                        buildSampleList(sampleName)
                    })
                    sampleList
                }).toList
            }

            getSamplesFromAllRunFolders(sampleName, runFolderReportToSampleListMap)
        }

        /**
         * The actual method - For every unique sample in the file, create a sample list
         */
        
        val distinctSampleNames = sampleList.map(f => f.getName()).distinct.toList
        
        //TODO
        println("Disctinct sample names: "  + distinctSampleNames)
        
        distinctSampleNames.map(sampleName => {
            (sampleName, getSampleList(sampleName))
        }).toMap
    }

    def getReference(sampleName: String): File = {
        val matchingSamples = sampleList.filter(p => p.getName().equals(sampleName))
        val referenceForSample = matchingSamples.map(sample => sample.getReference()).distinct
        require(referenceForSample.size == 1, "Found more than reference for the same sample. Sample name: " + sampleName)
        new File(referenceForSample(0))
    }

    def getUppmaxProjectId(): String = {
        project.getMetadata().getUppmaxprojectid()
    }
    
    /**
     * Private help methods used to construct ReadGroupInformations and ReadPaircontainer objects 
     */

    private def buildReadGroupInformation(sampleName: String, lane: Int, illuminaXMLReportReader: IlluminaXMLReportReader): ReadGroupInformation = {

        val readGroupId = illuminaXMLReportReader.getReadGroupID(sampleName, lane)
        val sequencingCenter = this.getSequencingCenter()
        val readLibrary = illuminaXMLReportReader.getReadLibrary(sampleName)
        val platform = this.getPlatform()
        val platformUnitId = illuminaXMLReportReader.getPlatformUnitID(sampleName, lane)

        new ReadGroupInformation(sampleName, readGroupId, sequencingCenter, readLibrary, platform, platformUnitId)

    }

    private def buildReadPairContainer(sampleFolder: Samplefolder, lane: Int): ReadPairContainer = {

        def getZerroPaddedIntAsString(i: Int, totalStringLength: Int): String = {

            def rep(n: Int)(f: => String): String = {
                if (n == 1)
                    f
                else
                    f + rep(n - 1)(f)
            }
            
            rep(totalStringLength - i.toString().length()) { "0" } + i
        }

        val sampleName = sampleFolder.getName()
        val folder = new File(sampleFolder.getPath())
        require(folder.isDirectory())

        val fastq1: List[File] = folder.listFiles().filter(f => f.getName().contains("_L" + getZerroPaddedIntAsString(lane, 3) + "_R1_")).toList
        val fastq2: List[File] = folder.listFiles().filter(f => f.getName().contains("_L" + getZerroPaddedIntAsString(lane, 3) + "_R2_")).toList

        if (fastq1.size == 1 && fastq2.size == 1)
            new ReadPairContainer(fastq1.get(0).getAbsoluteFile(), fastq2.get(0).getAbsoluteFile(), sampleName)
        else if (fastq1.size == 1 && fastq2.size == 0)
            new ReadPairContainer(fastq1.get(0), null, sampleName)
        else
            throw new FileNotFoundException("Problem with read pairs in folder: " + folder.getAbsolutePath() + " could not find suitable files. \n" +
                "the sample name was: " + sampleName + " and the sample lane: " + lane)
    }
}
    