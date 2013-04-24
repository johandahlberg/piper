package molmed.queue.setup
import java.io.File
import scala.collection.Seq
import scala.xml._
import collection.JavaConversions._
import java.io.FileNotFoundException

class SetupXMLReader(setupXML: File) extends SetupXMLReaderAPI{

    val xml = XML.loadFile(setupXML)
    
    
    def getSampleFolder(sampleName: String, runFolderName: String): File = {
        
        val runFolder = xml.\\("RunFolder").filter(f => f.\("@Report").text.equalsIgnoreCase(runFolderName))               
        val sampleFolderNode = runFolder.\("SampleFolder").filter(f => f.\("@Name").text.equalsIgnoreCase(sampleName))
        val sampleFolder = new File(sampleFolderNode.\("@Path").text).getAbsoluteFile()
                
        if(sampleFolder != null)
            sampleFolder
        else
            throw new FileNotFoundException("Could not find sample folder for: " + sampleName + ". Check that that folder exists.")
    }
    
    def getPlatform(): String = {
        xml.\\("Project")(0).attribute("Platform").get.text
    }
    
    def getUppmaxProjectId(): String = {
        xml.\\("Project")(0).attribute("UppmaxProjectId").get.text
    }
    
    def getSequencingCenter(): String = {
    
        xml.\\("Project")(0).attribute("SequencingCenter").get.text
        
    }
    
    def getProjectName(): String = {
        
        xml.\\("Project")(0).attribute("Name").get.text
        
    }
    
    def getSamples(): Map[String, Seq[SampleAPI]] = {
        // For each sample in setupXML, create a new sample instance and add it to the map
        
        var samples: scala.collection.mutable.Map[String, Seq[SampleAPI]] = scala.collection.mutable.Map.empty[String, Seq[SampleAPI]]
        
        val runFolderNodes = xml.\\("RunFolder")
        
        // Check the run folders on at the time for samples, and if that sample has already been seen in a previous
        // run folder, add it to the map under the same sample name.
        for (runFolderNode <- runFolderNodes){
        	
            val runFolderName = runFolderNode.\("@Report").text
            
            val sampleNodes = runFolderNode.\("SampleFolder")                          
            
            for(sampleNode <- sampleNodes) {
                
	            val illuminaXMLReportFile: File = new File(runFolderNode.attribute("Report").get.text)
	            val illuminaXMLReportReader: IlluminaXMLReportReader = new IlluminaXMLReportReader(illuminaXMLReportFile)
	            val sampleName = sampleNode.attribute("Name").get.text		            
	            
	            for (lane <- illuminaXMLReportReader.getLanes(sampleName)) {	 	                
	                
		            // If there is already a sample with this name in the in the map, add it to the list.
		            // if not, create a new list for that sample.
		            if(!samples.contains(sampleName))
		                samples(sampleName) = Seq(new Sample(sampleName, this, illuminaXMLReportReader, lane, runFolderName))		                		            
		            else
		                samples(sampleName) :+= new Sample(sampleName, this, illuminaXMLReportReader, lane, runFolderName)	            		                
		        }          	            
	        }
        }
    	samples.toMap
    }
    
   
    def getReference(sampleName: String): File = {        
        val sampleFolderNode = xml.\\("SampleFolder").find(node => node.attribute("Name").get.text.equalsIgnoreCase(sampleName))
        new File(sampleFolderNode.get.attribute("Reference").get.text).getAbsoluteFile()
    }    
}