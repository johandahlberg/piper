package molmed.utils

import java.io.File
import org.broadinstitute.gatk.queue.QScript

/**
 * Contains functions for running quality control (right now a very simple one which uses the GATK DepthOfCoverage walker).
 */
class AlignmentQCUtils(
    qscript: QScript,
    projectName: Option[String],
    generalUtils: GeneralUtils,
    pathToQualimap: File) {

  /**
   * @param bams			bam files to run qc on
   * @param outputBase		The path to write the output files to (a lot of different
   *                    	files with different names starting with that name will be created.
   * @param intervalFile    BED or GFF formatted interval file
   * @param isHuman    		Decide if gc content (and possibly other things) should be compared to expected distribution for human.
   * @param skipDup use flag --skip-duplicated with qualimap, i.e skip alignments that
   *            are marked as duplicated from the analysis
   * @return The base name for the qc files.
   *
   */
  def aligmentQC(
    bams: Seq[File],
    outputBase: File,
    isHuman: Boolean,
    skipDup: Int,
    intervalFile: Option[File] = None): Seq[File] = {

    // Run QC for each of them and output to a separate dir for each sample.
    val outputFiles =
      for (bam <- bams) yield {
        val baseName = GeneralUtils.swapExt(outputBase, bam, ".bam", ".qc")
        val logFile = GeneralUtils.swapExt(outputBase, bam, ".bam", ".qc.log")
        qscript.add(
          generalUtils.qualimapQC(
            bam,
            baseName,
            logFile,
            pathToQualimap,
            isHuman,
            skipDup,
            intervalFile))
        logFile
      }

    outputFiles
  }

  /**
   * Check genotype concordance compared to input vcf file
   *
   * @param bams			bam files to run qc on
   * @param outputBase		The path to write the output files to (a lot of different
   *                    	files with different names starting with that name will be created.
   * @param comparisonVcf    The genotypes to compare to
   * @return The resulting evaluation files
   *
   */
  def checkGenotypeConcordance(
    bamFiles: Seq[File],
    outputBase: File,
    comparisonVcf: File,
    qscript: QScript,
    gatkOptions: GATKConfig,
    projectName: Option[String],
    uppmaxConfig: UppmaxConfig,
    isLowPass: Boolean,
    isExome: Boolean,
    testMode: Boolean,
    minimumBaseQuality: Option[Int],
    skipVcfCompression: Boolean): Seq[File] = {

    val gatkOptionsWithGenotypingSnp = gatkOptions.copy(snpGenotypingVcf = Some(comparisonVcf))
    val bamTargets = bamFiles.map( bamFile => new GATKProcessingTarget(
          bamFile.getParentFile(),
          bamFile,
          skipDeduplication = false,
          keepPreBQSRBam = false,
          gatkOptions.intervalFile) )
    val variantCallingUtils = new VariantCallingUtils(gatkOptionsWithGenotypingSnp, projectName, uppmaxConfig)
    val variantCallingConfig = new VariantCallingConfig(
      qscript = qscript,
      variantCaller = Some(GATKUnifiedGenotyper),
      bamTargets = bamTargets,
      outputDir = outputBase,
      runSeparatly = true,
      isLowPass = isLowPass,
      isExome = isExome,
      noRecal = true,
      noIndels = true,
      testMode = testMode,
      minimumBaseQuality = minimumBaseQuality,
      noBAQ = true,
      skipAnnotation = true,
      skipVcfCompression = skipVcfCompression)

    val concordanceFiles = variantCallingUtils.checkGenotypeConcordance(variantCallingConfig)
    concordanceFiles
  }
}