package molmed.utils

import java.io.File
import org.broadinstitute.sting.queue.QScript

/**
 * Contains functions for running quality control (right now a very simple one which uses the GATK DepthOfCoverage walker).
 */
class AlignmentQCUtils(
    qscript: QScript,
    projectName: Option[String],
    generalUtils: GeneralUtils,
    pathToQualimap: File) {

  /**
   * @param bams		bam files to run qc on
   * @param outputBase	The path to write the output files to (a lot of different
   *                    files with different names starting with that name will be created. 
   * @return The base name for the qc files.
   *   
   */
  def aligmentQC(bams: Seq[File], outputBase: File): Seq[File] = {

    // Run QC for each of them and output to a separate dir for each sample.
    val outputFiles =
      for (bam <- bams) yield {
        val baseName = GeneralUtils.swapExt(outputBase, bam, ".bam", ".qc")
        val logFile = GeneralUtils.swapExt(outputBase, bam, ".bam", ".qc.log")
        qscript.add(generalUtils.qualimapQC(bam, baseName, logFile, pathToQualimap))
        logFile
      }

    outputFiles
  }      
}