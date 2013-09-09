package molmed.queue.extensions.RNAQC

import org.broadinstitute.sting.commandline._
import java.io.File
import org.broadinstitute.sting.queue.function.CommandLineFunction

class RNASeQC extends CommandLineFunction {

  analysisName = "RNASeQC"

  @Argument(doc = "String of format Sample ID|Bam File|Notes", shortName = "i", fullName = "input", required = true)
  var input: String = _

  @Input(doc = "Reference file in fasta format.", shortName = "r", fullName = "reference", required = true)
  var reference: File = _

  @Output(doc = "StdOut used for placeholding purposes", shortName = "place", fullName = "place_holder", required = true)
  var placeHolderFile: File = _

  @Input(doc = "GTF File defining the transcripts (must end in .gtf)", shortName = "t", fullName = "transcripts", required = true)
  var transcripts: File = _

  @Input(doc = "Output directory", shortName = "o", fullName = "output_dir", required = true)
  var output: File = _

  @Argument(doc = "The path to RNA-SeQC", shortName = "rnaseqc", fullName = "rna_seqc", required = false)
  var pathToRNASeQC: File = new File("resources/RNA-SeQC_v1.1.7.jar")
  
  var rRNATargetString: String = ""
  var downsampleString: String = ""

  override def commandLine = "java -jar " + pathToRNASeQC.getAbsolutePath() + " " +
    " -s " + input +
    " -r " + reference +
    " -t " + transcripts +
    rRNATargetString +
    downsampleString +
    " -o " + output +
    " > " + placeHolderFile
}