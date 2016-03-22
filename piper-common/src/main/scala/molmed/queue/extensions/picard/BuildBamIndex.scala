package molmed.queue.extensions.picard

import org.broadinstitute.gatk.utils.commandline._
import java.io.File
import org.broadinstitute.gatk.queue.extensions.picard.PicardBamFunction

/**
 * Queue extension for the BuildBamIndex program in Picard.
 */
class BuildBamIndex extends org.broadinstitute.gatk.queue.function.JavaCommandLineFunction {

  analysisName = "BuildBamIndex"
  javaMainClass = "picard.sam.BuildBamIndex"

  @Input(doc = "The input SAM or BAM file to index.  Must be coordinate sorted.", shortName = "input", fullName = "input_bam_file", required = true)
  var input: File = _

  @Output(doc = "The output file index", shortName = "output", fullName = "output_file", required = true)
  var output: File = _
  
  override def commandLine = super.commandLine +
  	required("INPUT=" + input) +
    required("OUTPUT=" + output)
}