package molmed.queue.setup

import java.io.File

object ReportReader {

  /**
   * Construct an appropriate report reader depending on the input format
   * @param report the report<.xml/.tsv> file
   * @return A appropriate report reader.
   */
  def apply(report: File): ReportReaderAPI = {
    val reportName = report.getName()
    reportName match {
      case r: String if r.endsWith(".xml") => 
        new IlluminaXMLReportReader(report)
      case r: String if r.endsWith(".tsv") =>
        new FlatFileReportReader(report)
      case _                               => 
        throw new Exception("Invalid file type of report: " + report + " only supports .xml and .tsv")
    }
  }
    
}