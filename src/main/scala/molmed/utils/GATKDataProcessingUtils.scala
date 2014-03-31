package molmed.utils

import net.sf.picard.reference.IndexedFastaSequenceFile
import org.broadinstitute.sting.queue.QScript
import java.io.File
import org.broadinstitute.sting.gatk.walkers.indels.IndelRealigner.ConsensusDeterminationModel

/**
 * Functions to run GATK data processing workflows.
 */
class GATKDataProcessingUtils(qscript: QScript, gatkOptions: GATKConfig, generalUtils: GeneralUtils, projectName: Option[String], uppmaxConfig: UppmaxConfig)
    extends GATKUtils(gatkOptions, projectName, uppmaxConfig) {

  /**
   * @param bams				bam files to process
   * @param outputDir			output folder
   * @param	cleaningModel		the cleaning model to use as a string. Allowed values: KNOWNS_ONLY, USE_SW, USE_READS. Default: USE_READS
   * @param	skipDeduplication	Skip deduplication (useful e.g. with amplicon based methods)
   * @param testMode			true if in test mode (don't add dates, etc. to files).
   * @return the processed bam files.
   */
  def dataProcessing(bams: Seq[File],
                     outputDir: File,
                     cleaningModel: String,
                     skipDeduplication: Boolean = false,
                     testMode: Boolean): Seq[File] = {

    def getIndelCleaningModel: ConsensusDeterminationModel = {
      if (cleaningModel == "KNOWNS_ONLY")
        ConsensusDeterminationModel.KNOWNS_ONLY
      else if (cleaningModel == "USE_SW")
        ConsensusDeterminationModel.USE_SW
      else
        ConsensusDeterminationModel.USE_READS
    }

    // sets the model for the Indel Realigner
    val cleanModelEnum = getIndelCleaningModel

    // if this is a 'knowns only' indel realignment run, do it only once for all samples.
    val globalIntervals = new File(outputDir + projectName.get + ".intervals")
    if (cleaningModel == ConsensusDeterminationModel.KNOWNS_ONLY)
      qscript.add(target(null, globalIntervals, cleanModelEnum))

    // put each sample through the pipeline
    val processedBams =
      for (bam <- bams) yield {

        val cleanedBam = GeneralUtils.swapExt(outputDir, bam, ".bam", ".clean.bam")
        val dedupedBam = GeneralUtils.swapExt(outputDir, bam, ".bam", ".clean.dedup.bam")
        val recalBam = if (!skipDeduplication) GeneralUtils.swapExt(outputDir, bam, ".bam", ".clean.dedup.recal.bam") else GeneralUtils.swapExt(outputDir, bam, ".bam", ".clean.recal.bam")

        // Accessory files
        val targetIntervals = if (cleaningModel == ConsensusDeterminationModel.KNOWNS_ONLY) { globalIntervals } else { GeneralUtils.swapExt(outputDir, bam, ".bam", ".intervals") }
        val metricsFile = GeneralUtils.swapExt(outputDir, bam, ".bam", ".metrics")
        val preRecalFile = GeneralUtils.swapExt(outputDir, bam, ".bam", ".pre_recal.table")
        val postRecalFile = GeneralUtils.swapExt(outputDir, bam, ".bam", ".post_recal.table")
        val preOutPath = GeneralUtils.swapExt(outputDir, bam, ".bam", ".pre")
        val postOutPath = GeneralUtils.swapExt(outputDir, bam, ".bam", ".post")
        val preValidateLog = GeneralUtils.swapExt(outputDir, bam, ".bam", ".pre.validation")
        val postValidateLog = GeneralUtils.swapExt(outputDir, bam, ".bam", ".post.validation")

        if (cleaningModel != ConsensusDeterminationModel.KNOWNS_ONLY)
          qscript.add(target(Seq(bam), targetIntervals, cleanModelEnum))

        qscript.add(clean(Seq(bam), targetIntervals, cleanedBam, cleanModelEnum, testMode))

        if (!skipDeduplication)
          qscript.add(generalUtils.dedup(cleanedBam, dedupedBam, metricsFile),
            cov(dedupedBam, preRecalFile, defaultPlatform = ""),
            recal(dedupedBam, preRecalFile, recalBam),
            cov(recalBam, postRecalFile, defaultPlatform = ""))
        else
          qscript.add(cov(cleanedBam, preRecalFile, defaultPlatform = ""),
            recal(cleanedBam, preRecalFile, recalBam),
            cov(recalBam, postRecalFile, defaultPlatform = ""))

        recalBam
      }

    processedBams
  }

}