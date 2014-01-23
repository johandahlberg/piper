package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import java.io.File
import org.broadinstitute.sting.commandline.Argument
import molmed.utils.Uppmaxable
import molmed.utils.UppmaxConfig
import molmed.utils.UppmaxJob

// Uppmaxable is a trait holding the necessary uppmax arguments
// to bring in.
class MyAwesomeQScript extends QScript with Uppmaxable {

  // An argument that should be passed to the QScript from the commandline 
  @Input(doc = "input fasta file", shortName = "i", required = true)
  var input: File = _

  // Where you define the pipeline
  def script() {

    // Load the uppmax config (projId and uppmaxQosFlag both live in the Uppmaxable
    // trait and can therefore be used here.
    val uppmaxConfig = new UppmaxConfig(this.projId, this.uppmaxQoSFlag)
    val uppmaxBase = new UppmaxBase(uppmaxConfig)

    // Defining names of output files
    val seqCounts = new File("sequence_counts.txt")
    val totalNumberOfReads = new File("total_read_nbr.txt")

    // Add jobs to dependency graph
    add(uppmaxBase.NaiveSequenceCounter(input, seqCounts))
    add(uppmaxBase.SumTotalNumberOfReads(seqCounts, totalNumberOfReads))

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
  }

}