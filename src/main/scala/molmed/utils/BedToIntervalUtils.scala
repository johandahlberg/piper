package molmed.utils

import java.io.PrintWriter
import org.broadinstitute.sting.queue.function.InProcessFunction
import java.io.File
import org.broadinstitute.sting.commandline.Input
import scala.io.Source
import org.broadinstitute.sting.commandline.Output
import net.sf.samtools.SAMFileHeader
import net.sf.samtools.SAMTextHeaderCodec
import net.sf.samtools.SAMFileReader
import scala.collection.JavaConversions._
import org.broadinstitute.sting.commandline.Argument

object BedToIntervalUtils {

  trait IntervalConverter {

    val doNotConvert: Boolean

    // Uses + strand if no strand if is provided. Unfortunately the is no NA option for this field in the 
    // Picard IntervalList.
    def intervalFormatString(contig: String, start: String, end: String, strand: Option[String], intervalName: String): String =
      "%s\t%s\t%s\t%s\t%s".format(contig, start, end, strand.getOrElse("+"), intervalName)

    def conversions(s: String) = if (doNotConvert) s else s.replace("chrM", "MT").replace("chr", "")

    def formatFromCovered(split: Array[String]): String = {
      intervalFormatString(conversions(split(0)), (split(1).toInt + 1).toString, split(2), None, split(3))
    }

    def formatFromAmplicons(split: Array[String]): String = {
      if (split.length == 6)
        intervalFormatString(
          conversions(split(0)), (split(1).toInt + 1).toString, split(2),
          if (split(5) != null) Some(split(5)) else None,
          split(3))
      else if (split.length == 4)
        intervalFormatString(
          conversions(split(0)), (split(1).toInt + 1).toString, split(2),
          None,
          split(3))
      else
        throw new Exception("Unknown number of fields in amplicon bed file. " +
          "The number of fields  should be 6 (if there is strand information) " +
          "or 5 (if there is no strand info). Number of fields was: " +
          split.length)
    }

    def writeIntervals(bed: File, intervalFile: File, bam: File, formatFrom: Array[String] => String): Unit = {

      def getSamHeader(bam: File): SAMFileHeader = {
        val samReader = new SAMFileReader(bam)
        samReader.getFileHeader
      }

      val header = getSamHeader(bam)
      header.setProgramRecords(List())
      header.setReadGroups(List())

      val writer = new PrintWriter(intervalFile)

      val codec = new SAMTextHeaderCodec();
      codec.encode(writer, header)

      for (row <- Source.fromFile(bed).getLines.drop(2)) {
        val split = row.split("\t")
        val intervalEntry = formatFrom(split)
        writer.println(intervalEntry)
      }
      writer.close()
    }

  }
  case class convertCoveredToIntervals(@Input var bed: File, @Output var intervalFile: File, @Input var bam: File, @Argument var skipNameConversion: Boolean) extends InProcessFunction with IntervalConverter {

    val doNotConvert = skipNameConversion

    def run(): Unit = {
      writeIntervals(bed, intervalFile, bam, formatFromCovered)
    }
  }

  case class convertBaitsToIntervals(@Input var bed: File, @Output var intervalFile: File, @Input var bam: File, @Argument var skipNameConversion: Boolean) extends InProcessFunction with IntervalConverter {

    val doNotConvert = skipNameConversion

    def run(): Unit = {
      writeIntervals(bed, intervalFile, bam, formatFromAmplicons)
    }
  }

}