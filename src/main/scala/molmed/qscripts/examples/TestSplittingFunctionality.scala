package molmed.qscripts

import org.broadinstitute.gatk.queue.QScript
import java.io.File
import molmed.utils.GeneralUtils
import molmed.utils.UppmaxConfig
import molmed.utils.SplitFilesAndMergeByChromosome

class TestSplittingFunctionality extends QScript {

  // An argument that should be passed to the qscript from the commandline 
  @Input(doc = "input bam file", shortName = "b", required = true)
  var inputBam: File = _

  // An argument that should be passed to the qscript from the commandline 
  @Input(doc = "input fasta dict file", shortName = "d", required = true)
  var inputFastaDict: File = _

  // Where you define the pipeline
  def script() {

    val uppmaxConfig = new UppmaxConfig()
    val generalUtils = new GeneralUtils(Some("test_split"), uppmaxConfig)

    val splitBam =
      SplitFilesAndMergeByChromosome.splitByChromosome(
        this,
        inputBam,
        inputFastaDict,
        3,
        generalUtils,
        asIntermediate = false)

    val dedupedBams =
      for (bam <- splitBam) yield {
        val outBam = GeneralUtils.swapExt(bam, ".bam", ".dedup.bam")
        val metrics = GeneralUtils.swapExt(bam, ".bam", ".dedup.metrics")
        this.add(generalUtils.dedup(bam, outBam: File, metrics, asIntermediate = true))
        outBam
      }

    val mergedBam = GeneralUtils.swapExt(inputBam, ".bam", ".deduped.merged.bam")
    SplitFilesAndMergeByChromosome.merge(this, dedupedBams, mergedBam, asIntermediate = false, generalUtils)

  }

  //  case class sleeper() extends CommandLineFunction {
  //    
  //    def commandLine = "sleep 600"
  //    
  //  }
}