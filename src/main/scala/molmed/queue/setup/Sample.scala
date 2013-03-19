package molmed.queue.setup
import java.io.File
import collection.JavaConversions._
import java.io.FileNotFoundException

trait SampleAPI {
    def getFastqs(): ReadPairContainer
    def getBwaStyleReadGroupInformationString(): String
    def getTophatStyleReadGroupInformationString(): String
    def getReference(): File
    def getSampleName(): String
}

class Sample(sampleName: String, setupXMLReader: SetupXMLReaderAPI, illuminaXMLReportReader: IlluminaXMLReportReaderAPI, sampleLane: Int, runFolderName: String) extends SampleAPI {

    /**
     * Auxilary case class for storing read group information
     */

    case class ReadGroupInformation(readGroupId: String, sequencingCenter: String, readLibrary: String, platform: String, platformUnitId: String) {}

    /**
     * Private variables
     */
    private val readPairContainer: ReadPairContainer = {
        val sampleDirectory: File = setupXMLReader.getSampleFolder(sampleName, runFolderName)

        val fastq1: List[File] = sampleDirectory.listFiles().filter(f => f.getName().contains("_L" + getZerroPaddedIntAsString(sampleLane, 3) + "_R1_")).toList
        val fastq2: List[File] = sampleDirectory.listFiles().filter(f => f.getName().contains("_L" + getZerroPaddedIntAsString(sampleLane, 3) + "_R2_")).toList

        if (fastq1.size == 1 && fastq2.size == 1)
            new ReadPairContainer(fastq1.get(0).getAbsoluteFile(), fastq2.get(0).getAbsoluteFile(), sampleName)
        else if (fastq1.size == 1 && fastq2.size == 0)
            new ReadPairContainer(fastq1.get(0), null, sampleName)
        else
            throw new FileNotFoundException("Problem with read pairs in folder: " + sampleDirectory.getAbsolutePath() + " could not find suitable files. \n" +
                "the sample name was: " + sampleName + " and the sample lane: " + sampleLane)
    }

    private val readGroupInfo: ReadGroupInformation = {
        val readGroupId = illuminaXMLReportReader.getReadGroupID(sampleName, sampleLane)
        val sequencingCenter = setupXMLReader.getSequencingCenter()
        val readLibrary = illuminaXMLReportReader.getReadLibrary(sampleName)
        val platform = setupXMLReader.getPlatform()
        val platformUnitId = illuminaXMLReportReader.getPlatformUnitID(sampleName, sampleLane)

        new ReadGroupInformation(readGroupId, sequencingCenter, readLibrary, platform, platformUnitId)
    }

    private val reference: File = {
        setupXMLReader.getReference(sampleName)
    }

    /**
     * Public methods
     */

    def getSampleName(): String = {
        sampleName
    }

    def getFastqs(): ReadPairContainer = {
        readPairContainer
    }

    def getBwaStyleReadGroupInformationString(): String = {
        parseToBwaApprovedString(readGroupInfo)
    }

    def getTophatStyleReadGroupInformationString(): String = {
        parseToTophatApprovedString(readGroupInfo)
    }

    def getReference(): File = {
        reference
    }

    override def equals(that: Any): Boolean = {

        that.isInstanceOf[Sample] &&
            this.sampleName.equals((that.asInstanceOf[Sample]).getSampleName())
    }

    override def hashCode(): Int = {
        sampleName.hashCode()
    }

    /**
     * Private methods
     */
    
    private def parseToTophatApprovedString(readGroupInfo: ReadGroupInformation): String = {
        
        /**
         * Format specification from the tophat manual.
         * 
         * SAM Header Options (for embedding sequencing run metadata in output):
         * --rg-id                        <string>    (read group ID)
         * --rg-sample                    <string>    (sample ID)
         * --rg-library                   <string>    (library ID)
         * --rg-description               <string>    (descriptive string, no tabs allowed)
         * --rg-platform-unit             <string>    (e.g Illumina lane ID)
         * --rg-center                    <string>    (sequencing center name)
         * --rg-date                      <string>    (ISO 8601 date of the sequencing run)
         * --rg-platform                  <string>    (Sequencing platform descriptor)
         *
         */
        
        " --rg-id " + readGroupInfo.readGroupId + " --rg-sample " + sampleName + " --rg-library " + readGroupInfo.readLibrary + " --rg-platform-unit " + readGroupInfo.platformUnitId + 
        " --rg-center " + readGroupInfo.sequencingCenter + " --rg-platform " + readGroupInfo.platform
    }
    
    private def parseToBwaApprovedString(readGroupInfo: ReadGroupInformation): String = {

        // The form which bwa wants, according to their manual is: @RG\tID:foo\tSM:bar
        val readGroupHeader: String = "\"" + """@RG\tID:""" + readGroupInfo.readGroupId + """\\tSM:""" + sampleName + """\\tCN:""" + readGroupInfo.sequencingCenter + """\\tLB:""" + readGroupInfo.readLibrary +
            """\\tPL:""" + readGroupInfo.platform + """\\tPU:""" + readGroupInfo.platformUnitId + "\""

        return readGroupHeader
    }
    
    

    private def getZerroPaddedIntAsString(i: Int, totalStringLength: Int): String = {
        rep(totalStringLength - i.toString().length()) { "0" } + i
    }

    private def rep(n: Int)(f: => String): String = {
        if (n == 1)
            f
        else {
            f + rep(n - 1)(f)
        }
    }
}