package molmed.utils

import java.io.File
import org.broadinstitute.gatk.tools.walkers.indels.IndelRealigner.ConsensusDeterminationModel

/**
 * Help class handling each GATK processing target. Storing input files, creating output filenames etc.
 */
class GATKProcessingTarget(outputDir: File,
                           val bam: File,
                           val skipDeduplication: Boolean,
                           val bqsrOnTheFly: Boolean,
                           val globalIntervals: Option[File]) {

    // Processed bam files
    val cleanedBam = GeneralUtils.swapExt(outputDir, bam, ".bam", ".clean.bam")
    val dedupedBam = if (!skipDeduplication) GeneralUtils.swapExt(outputDir, cleanedBam, ".bam", ".dedup.bam") else cleanedBam
    val recalBam = if (!bqsrOnTheFly) GeneralUtils.swapExt(outputDir, dedupedBam, ".bam", ".recal.bam") else dedupedBam
    val processedBam = recalBam

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
