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
   * @return The base name for the qc files.
   *
   */
  def aligmentQC(
    bams: Seq[File],
    outputBase: File,
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
    bams: Seq[File],
    outputBase: File,
    comparisonVcf: File,
    qscript: QScript,
    gatkOptions: GATKConfig,
    projectName: Option[String],
    uppmaxConfig: UppmaxConfig,
    isLowPass: Boolean,
    isExome: Boolean,
    testMode: Boolean,
    minimumBaseQuality: Option[Int]): Seq[File] = {

    val gatkOptionsWithGenotypingSnp = gatkOptions.copy(snpGenotypingVcf = Some(comparisonVcf))
    
    val variantCallingUtils = new VariantCallingUtils(gatkOptionsWithGenotypingSnp, projectName, uppmaxConfig)
    val variantCallingConfig = new VariantCallingConfig(
      qscript = qscript,
      variantCaller = Some(GATKUnifiedGenotyper),
      bams = bams,
      outputDir = outputBase,
      runSeparatly = true,
      isLowPass = isLowPass,
      isExome = isExome,
      noRecal = true,
      noIndels = true,
      testMode = testMode,
      minimumBaseQuality = minimumBaseQuality,
      noBAQ = true)

    val variantsAndEvalFile = variantCallingUtils.performVariantCalling(variantCallingConfig)
    variantsAndEvalFile.filter(p => p.getName().endsWith("snp.eval"))
  }
}