package se.uu.medsci.queue.setup
import java.io.File
import collection.JavaConversions._
import java.io.FileNotFoundException


trait SampleAPI {
    def getFastqs(): ReadPairContainer    
    def getReadGroupInformation(): String   
    def getReference(): File
    def getSampleName(): String
}


class Sample(sampleName: String, setupXMLReader: SetupXMLReaderAPI, illuminaXMLReportReader: IlluminaXMLReportReaderAPI, sampleLane: Int, runFolderName: String) extends SampleAPI {

    /**
     * Private variables
     */
    private val readPairContainer: ReadPairContainer = {
	    val sampleDirectory: File = setupXMLReader.getSampleFolder(sampleName, runFolderName)	    
	    
	    val fastq1: List[File] = sampleDirectory.listFiles().filter(f => f.getName().contains("_L"+ getZerroPaddedIntAsString(sampleLane, 3) + "_R1_")).toList
	    val fastq2: List[File] = sampleDirectory.listFiles().filter(f => f.getName().contains("_L"+ getZerroPaddedIntAsString(sampleLane, 3) + "_R2_")).toList  	 

	    if(fastq1.size == 1 && fastq2.size == 1)
	    	new ReadPairContainer(fastq1.get(0).getAbsoluteFile(), fastq2.get(0).getAbsoluteFile(), sampleName)    
	    else if (fastq1.size == 1 && fastq2.size == 0)
	    	new ReadPairContainer(fastq1.get(0), null, sampleName)
	    else 
	        throw new FileNotFoundException("Problem with read pairs in folder: " + sampleDirectory.getAbsolutePath() + " could not find suitable files. \n" +
	        		"the sample name was: " + sampleName + " and the sample lane: " + sampleLane)	   
    }
    
    private val readGroupInfo: String = {
        val readGroupId = illuminaXMLReportReader.getReadGroupID(sampleName, sampleLane)
        val sequencingCenter = setupXMLReader.getSequencingCenter()
        val readLibrary = illuminaXMLReportReader.getReadLibrary(sampleName)
        val platform = setupXMLReader.getPlatform()
        val platformUnitId = illuminaXMLReportReader.getPlatformUnitID(sampleName, sampleLane)
        
        parseToBwaApprovedString(readGroupId, sequencingCenter, readLibrary, platform, platformUnitId, sampleName)        
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
    
    def getReadGroupInformation(): String = {
        readGroupInfo
    }
    
    
    def getReference(): File = {
        reference
    }
    
    override
    def equals(that: Any): Boolean = {                
        
        that.isInstanceOf[Sample] && 
        this.sampleName.equals((that.asInstanceOf[Sample]).getSampleName())
    }
    
    override
    def hashCode(): Int = {
        sampleName.hashCode()
    }

    /**
     * Private methods
     */
    private def parseToBwaApprovedString(readGroupId: String, sequencingCenter: String, readLibrary: String,
          					   platform: String, platformUnit: String, sampleName: String): String ={
      
      // The form which bwa wants, according to their manual is: @RG\tID:foo\tSM:bar
      val readGroupHeader: String = "\"" + """@RG\tID:""" + readGroupId + """\\tSM:""" + sampleName + """\\tCN:""" + sequencingCenter + """\\tLB:""" + readLibrary + 
      """\\tPL:""" + platform + """\\tPU:""" + platformUnit + "\""     
      
      return readGroupHeader
    }    
    
    private def getZerroPaddedIntAsString(i: Int, totalStringLength: Int): String = {
        rep(totalStringLength - i.toString().length()) {"0"} + i        
    }
    
    private def rep(n: Int)(f: => String): String = { 
        if (n == 1) 
        	f 
        else{
            f + rep(n-1)(f)
        }            
    }
}