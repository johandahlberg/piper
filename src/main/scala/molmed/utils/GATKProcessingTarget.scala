package molmed.utils

import java.io.File
import org.broadinstitute.gatk.tools.walkers.indels.IndelRealigner.ConsensusDeterminationModel

case class GATKOutputFile(file: File,
                          isIntermediate: Boolean)

/**
 * Help class handling each GATK processing target. Storing input files, creating output filenames etc.
 */
class GATKProcessingTarget(outputDir: File,
                           val bam: File,
                           val skipDeduplication: Boolean,
                           val keepPreBQSRBam: Boolean,
                           val globalIntervals: Option[File]) {

    // Processed bam files
    val cleanedBam = new GATKOutputFile(
      GeneralUtils.swapExt(outputDir, bam, ".bam", ".clean.bam"),
      !(keepPreBQSRBam && skipDeduplication))
    val dedupedBam =
      if (!skipDeduplication)
        new GATKOutputFile(GeneralUtils.swapExt(outputDir, cleanedBam.file, ".bam", ".dedup.bam"), !keepPreBQSRBam)
      else
        cleanedBam
    val recalBam = new GATKOutputFile(
      GeneralUtils.swapExt(outputDir, dedupedBam.file, ".bam", ".recal.bam"),
      keepPreBQSRBam)

    // the preBQSR or postBQSR BAM as the final product
    def processedBam: GATKOutputFile = if (keepPreBQSRBam) dedupedBam else recalBam

    // Accessory files
    val targetIntervals = globalIntervals.getOrElse(GeneralUtils.swapExt(outputDir, bam, ".bam", ".intervals"))
    val metricsFile = GeneralUtils.swapExt(outputDir, bam, ".bam", ".metrics")
    val preRecalFile = GeneralUtils.swapExt(outputDir, bam, ".bam", ".pre_recal.table")
    val postRecalFile = GeneralUtils.swapExt(outputDir, bam, ".bam", ".post_recal.table")
    val preOutPath = GeneralUtils.swapExt(outputDir, bam, ".bam", ".pre")
    val postOutPath = GeneralUtils.swapExt(outputDir, bam, ".bam", ".post")
    val preValidateLog = GeneralUtils.swapExt(outputDir, bam, ".bam", ".pre.validation")
    val postValidateLog = GeneralUtils.swapExt(outputDir, bam, ".bam", ".post.validation")

}
