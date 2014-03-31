package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.commandline
import collection.JavaConversions._
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.util.QScriptUtils
import molmed.utils.GATKUtils
import molmed.utils.Uppmaxable
import molmed.utils.GATKConfig
import molmed.utils.UppmaxConfig
import molmed.utils.UppmaxXMLConfiguration

/**
 * Simple Alignment quality control using the DepthOfCoverage walker from GATK.
 */
class AlignmentQC extends QScript with UppmaxXMLConfiguration {
  qscript =>

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "input BAM file - or list of BAM files to QC", fullName = "input", shortName = "i", required = true)
  var input: File = _

  @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = true)
  var reference: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "an intervals file to be used by GATK - output bams at intervals only", fullName = "gatk_interval_file", shortName = "intervals", required = false)
  var intervals: File = _

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {

    // Get the bam files to analyze
    val bams = QScriptUtils.createSeqFromFile(input)
    val uppmaxConfig = loadUppmaxConfigFromXML()
    val gatkOptions = new GATKConfig(reference, 8, 1, Some(intervals), None, None)
    val gatkUtils = new GATKUtils(gatkOptions, projectName, uppmaxConfig)

    // Run QC for each of them and output to a separate dir for each sample.
    for (bam <- bams) {
      val outDir = createOutputDir(bam)
      add(gatkUtils.DepthOfCoverage(bam, outDir))
    }
  }

  def createOutputDir(file: File) = {
    val outDir = {
      val basename = file.getName().replace(".bam", "")
      if (outputDir == "") new File(basename) else new File(outputDir + "/" + basename)
    }
    outDir.mkdirs()
    outDir
  }
}