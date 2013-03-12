package se.uu.medsci.queue.setup
import java.io.File
import collection.JavaConversions._
import scala.xml.XML

// Trait, used as interface to be able to stub the class for
// testing purposes
trait IlluminaXMLReportReaderAPI {
    def getReadLibrary(sampleName: String): String
    def getFlowcellId(): String
    def getPlatformUnitID(sampleName: String, lane: Int): String
    def getReadGroupID(sampleName: String, lane: Int): String    
    def getLanes(sampleName: String): List[Int]  
}


class IlluminaXMLReportReader(report: File) extends IlluminaXMLReportReaderAPI {
    
    val xml = XML.loadFile(report)
    
    def getReadLibrary(sampleName: String): String = {
         getSampleEntry(sampleName).\\("Read")(0).attribute("LibraryName").get.get(0).text         
     }
    def getFlowcellId(): String = {
        xml.\\("MetaData")(0).attribute("FlowCellId").get.text        
    }
    def getPlatformUnitID(sampleName: String, lane: Int): String = {
        getFlowcellId() + "." + sampleName + "." + lane
    }
    
    def getReadGroupID(sampleName: String, lane: Int): String = {
         getFlowcellId() + "." + sampleName + "." + lane
    }
    
    def getLanes(sampleName: String): List[Int] = {
        val sampleEntry = getSampleEntry(sampleName)
        val list = sampleEntry.\\("Lane").map(n => (n \ "@Id").text.toInt).toList        
        return list
    }
    
    private def getSampleEntry(sampleName: String): scala.xml.NodeSeq = {
        val allSamples = xml.\\("Sample")       
        val filteredSamples = allSamples.filter(_.\("@Id").text.equalsIgnoreCase(sampleName))
        filteredSamples       
    }
}