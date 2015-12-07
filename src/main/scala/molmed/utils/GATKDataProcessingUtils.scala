package molmed.utils

import org.broadinstitute.gatk.queue.QScript
import java.io.File
import org.broadinstitute.gatk.tools.walkers.indels.IndelRealigner.ConsensusDeterminationModel

/**
 * Functions to run GATK data processing workflows.
 */
class GATKDataProcessingUtils(
    qscript: QScript,
    gatkOptions: GATKConfig,
    generalUtils: GeneralUtils,
    projectName: Option[String], 
    uppmaxConfig: UppmaxConfig)
    extends GATKUtils(gatkOptions, projectName, uppmaxConfig) {

  /**
   * @param bams				bam files to process
   * @param outputDir			output folder
   * @param	cleaningModel		the cleaning model to use as a string.
   * 						    Allowed values: KNOWNS_ONLY, USE_SW, USE_READS.
   *                            Default: USE_READS
   * @param	skipDeduplication	Skip deduplication (useful e.g. with amplicon
   * 							based methods)
   * @param testMode			true if in test mode 
   * 						    (don't add dates, etc. to files).
   * @return the processed bam files.
   */
  def dataProcessing(bams: Seq[File],
                     outputDir: File,
                     cleaningModel: String,
                     skipDeduplication: Boolean = false,
                     testMode: Boolean,
                     intermediateStep: Boolean): Seq[GATKProcessingTarget] = {

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
    val processedTargets: Seq[GATKProcessingTarget] =
      for (bam <- bams) yield {

        val processedTarget = new GATKProcessingTarget(
            outputDir,
            bam,
            skipDeduplication,
            this.gatkOptions.keepPreBQSRBam,
            if (cleaningModel == ConsensusDeterminationModel.KNOWNS_ONLY) Some(globalIntervals) else None)

        if (cleaningModel != ConsensusDeterminationModel.KNOWNS_ONLY)
          qscript.add(target(Seq(processedTarget.bam), processedTarget.targetIntervals, cleanModelEnum))

        // realign
        qscript.add(clean(Seq(processedTarget.bam), processedTarget.targetIntervals, processedTarget.cleanedBam.file, cleanModelEnum, testMode, asIntermediate = intermediateStep || processedTarget.cleanedBam.isIntermediate))
        // mark duplicates unless we're told not to
        if (!skipDeduplication)
            qscript.add(generalUtils.dedup(processedTarget.cleanedBam.file, processedTarget.dedupedBam.file, processedTarget.metricsFile, asIntermediate = intermediateStep || processedTarget.dedupedBam.isIntermediate))
        // calculate recalibration covariates
        qscript.add(cov(processedTarget.dedupedBam.file, processedTarget.preRecalFile, defaultPlatform = "", asIntermediate = intermediateStep))
        // apply recalibration
        qscript.add(recal(processedTarget.dedupedBam.file, processedTarget.preRecalFile, processedTarget.recalBam.file, asIntermediate = intermediateStep || processedTarget.recalBam.isIntermediate))
        // calculate recalibration covariates after recalibration
        qscript.add(cov(processedTarget.recalBam.file, processedTarget.postRecalFile, defaultPlatform = "", asIntermediate = intermediateStep))

        processedTarget
      }

    processedTargets
  }

}