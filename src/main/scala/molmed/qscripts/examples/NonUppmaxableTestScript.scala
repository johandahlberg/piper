package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import java.io.File
import org.broadinstitute.sting.commandline.Argument

class NonUppmaxableTestScript extends QScript {

  // An argument that should be passed to the qscript from the commandline 
  @Input(doc = "input fasta file", shortName = "i", required = true)
  var input: File = _

  // Where you define the pipeline
  def script() {

    // Defining names of output files
    val seqCounts = new File("sequence_counts.txt")
    val totalNumberOfReads = new File("total_read_nbr.txt")

    // Add jobs to dependency graph
    add(NaiveSequenceCounter(input, seqCounts))
    add(SumTotalNumberOfReads(seqCounts, totalNumberOfReads))

  }

  case class SumTotalNumberOfReads(@Input seqCounts: File, @Output totalNumberOfReads: File) extends CommandLineFunction {
    // Another way to define the commandline
    def commandLine = required("cat") +
      required(seqCounts) +
      required(" |  awk \'{sum=sum+$1} END{print sum}\' >  ", escape = false) +
      required(totalNumberOfReads)
  }

  case class NaiveSequenceCounter(@Input fastaFile: File, @Output sequenceCounts: File) extends CommandLineFunction {
    // Simplest possible way to define commandline 
    def commandLine = "cat " + fastaFile +
      " | grep -v \"^>\" | sort | uniq -c >" +
      sequenceCounts
  }
}