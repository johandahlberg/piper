package molmed.queue.extensions.picard

import org.broadinstitute.sting.commandline._
import java.io.File
import org.broadinstitute.sting.queue.extensions.picard.PicardBamFunction

/**
 * Queue extension for the BuildBamIndex program in Picard.
 */
class BuildBamIndex extends org.broadinstitute.sting.queue.function.JavaCommandLineFunction {

  analysisName = "BuildBamIndex"
  javaMainClass = "net.sf.picard.sam.BuildBamIndex"

  @Input(doc = "The input SAM or BAM file to index.  Must be coordinate sorted.", shortName = "input", fullName = "input_bam_file", required = true)
  var input: File = _

  @Output(doc = "The output file index", shortName = "output", fullName = "output_file", required = true)
  var output: File = _
  
  override def commandLine = super.commandLine +
  	required("INPUT=" + input) +
    required("OUTPUT=" + output)
}