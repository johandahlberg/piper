package molmed.qscripts.examples

import org.broadinstitute.sting.queue.QScript
import molmed.utils.Uppmaxable
import molmed.utils.UppmaxConfig
import molmed.utils.UppmaxJob
import org.broadinstitute.sting.queue.util.QScriptUtils
import java.io.PrintWriter
import scala.io.Source

// Uppmaxable is a trait holding the necessary uppmax arguments
// to bring in.
class MyAwesomeQScript extends QScript with Uppmaxable {

  // An argument that should be passed to the QScript from the commandline 
  @Input(doc = "input fasta file, or list of fasta files", shortName = "i", required = true)
  var input: File = _

  // Where you define the pipeline
  def script() {

    // Create a sequence of files if a file which lists more than
    // one fasta file is used otherwise just returns the file passed.
    val fastaFiles = QScriptUtils.createSeqFromFile(input)

    // Load the uppmax config (projId and uppmaxQosFlag both live in the Uppmaxable
    // trait and can therefore be used here.
    val uppmaxConfig = new UppmaxConfig(this.projId, this.uppmaxQoSFlag)
    val uppmaxBase = new UppmaxBase(uppmaxConfig)

    for (fastaFile <- fastaFiles) {

      // Defining names of output files
      val seqCounts = new File(fastaFile + ".sequence_counts.txt")
      val totalNumberOfReads = new File(fastaFile + ".total_read_nbr.txt")
      val baseCounts = new File(fastaFile + ".base_counts.txt")
      val report = new File(fastaFile + ".report.txt")

      // Add jobs to dependency graph
      add(uppmaxBase.NaiveSequenceCounter(fastaFile, seqCounts))
      add(uppmaxBase.SumTotalNumberOfReads(seqCounts, totalNumberOfReads))
      add(uppmaxBase.BaseCount(fastaFile, baseCounts))
      add(CreateReport(totalNumberOfReads, seqCounts, baseCounts, report))
      
    }

  }

  // Now our two classes are wrapped in a Uppmax base class which extends the
  // UppmaxJob class. This holds the utility classes to specify resource
  // usage.
  class UppmaxBase(uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig) {

    // Note the "extends OneCoreJob" part, which specifies that this job should request one core from the cluster.
    // Other 
    case class SumTotalNumberOfReads(@Input seqCounts: File, @Output totalNumberOfReads: File) extends OneCoreJob {
      // Another way to define the commandline
      def commandLine = required("cat") +
        required(seqCounts) +
        required(" |  awk \'{sum=sum+$1} END{print sum}\' >  ", escape = false) +
        required(totalNumberOfReads)
    }

    case class NaiveSequenceCounter(@Input fastaFile: File, @Output sequenceCounts: File) extends OneCoreJob {
      // Simplest possible way to define commandline 
      def commandLine = "cat " + fastaFile +
        " | grep -v \"^>\" | sort | uniq -c >" +
        sequenceCounts
    }

    case class BaseCount(@Input fastaFile: File, @Output baseCounts: File) extends OneCoreJob {
      def commandLine =
        """cat  """ + fastaFile +
          """ | grep -v "^>" | """ +
          """awk 'BEGIN{a=0; c=0; g=0; t=0;} {a+=gsub("A",""); c+=gsub("C",""); g+=gsub("G",""); t+=gsub("T","");} END{print a"\t"c"\t"g"\t"t}'""" +
          """ > """ +
          baseCounts
    }
  }

  case class CreateReport(@Input totalNumberOfReadsFile: File, @Input sequenceCountsFile: File,
                          @Input baseCountsFile: File, @Output report: File) extends InProcessFunction {

    def run() = {
      val writer = new PrintWriter(report)

      val totalNumberOfReads = Source.fromFile(totalNumberOfReadsFile).getLines.mkString
      writer.println("Total number of reads: " + totalNumberOfReads)

      writer.println(List("A", "C", "G", "T").mkString("\t"))
      val baseCounts = Source.fromFile(baseCountsFile).mkString
      writer.println(baseCounts)

      writer.println("List of the 10 most common sequences:")
      val sequenceCounts = Source.fromFile(sequenceCountsFile).getLines.take(10)
      writer.println(sequenceCounts.mkString("\n"))

      writer.close()
    }
  }

}