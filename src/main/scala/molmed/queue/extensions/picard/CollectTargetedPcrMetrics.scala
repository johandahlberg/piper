package molmed.queue.extensions.picard

import org.broadinstitute.sting.commandline._
import java.io.File
import net.sf.picard.analysis.MetricAccumulationLevel
import org.broadinstitute.sting.queue.extensions.picard.PicardMetricsFunction

class CollectTargetedPcrMetrics extends org.broadinstitute.sting.queue.function.JavaCommandLineFunction with PicardMetricsFunction {

  analysisName = "CollectTargetedPcrMetrics"
  javaMainClass = "net.sf.picard.analysis.directed.CollectTargetedPcrMetrics"

  @Input(doc = "The input SAM or BAM files to analyze.  Must be coordinate sorted.", shortName = "input", fullName = "input_bam_files", required = true)
  var input: Seq[File] = Nil

  @Output(doc = "The output file to write statistics to", shortName = "output", fullName = "output_file", required = true)
  var output: File = _

  @Output(doc = "Per amplicon coverage file", shortName = "ptcf", fullName = "per_target_output_file", required = true)
  var perTargetOutputFile: File = _

  @Argument(doc = "Interval list with targets", shortName = "targets", fullName = "target_list", required = true)
  var targets: File = _

  @Argument(doc = "Custom amplicion set", shortName = "ampl", fullName = "amplicons", required = true)
  var amplicons: File = _

  @Argument(doc = "Reference file", shortName = "reference", fullName = "reference", required = true)
  var reference: File = _

  @Argument(doc = "Metrics acumulation level", shortName = "mal", fullName = "metricsAccumulationLevel", required = false)
  val metricsAccumulationLevel: String = "ALL_READS"  

  override def inputBams = input
  override def outputFile = output
  override def commandLine = super.commandLine +
    required("AMPLICON_INTERVALS=" + amplicons) +
    required("TARGET_INTERVALS=" + targets) +
    required("REFERENCE_SEQUENCE=" + reference) +
    optional("METRIC_ACCUMULATION_LEVEL=" + metricsAccumulationLevel) +
    optional("PER_TARGET_COVERAGE=" + perTargetOutputFile)

}