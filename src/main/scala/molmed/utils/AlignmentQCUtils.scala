package molmed.utils

import java.io.File
import org.broadinstitute.sting.queue.QScript

/**
 * Contains functions for running quality control (right now a very simple one which uses the GATK DepthOfCoverage walker).
 */
class AlignmentQCUtils(qscript: QScript, gatkOptions: GATKOptions, projectName: Option[String], uppmaxConfig: UppmaxConfig)
  extends GATKUtils(gatkOptions, projectName, uppmaxConfig) {

  /**
   * @param bams		bam files to run qc on
   * @param outputDir	root dir to write output to
   * @return tupple of the bam filed qc'ed and a boolean indicating if it passed the qc criterion or not.
   * 
   * @todo Note: The criterion part of this is not yet implemented!  
   */
  def aligmentQC(bams: Seq[File], outputDir: File): Seq[(File, Boolean)] = {

    def createOutputDir(file: File) = {
      val outDir = {
        val basename = file.getName().replace(".bam", "")
        if (outputDir == "") new File(basename) else new File(outputDir + "/" + basename)
      }
      outDir.mkdirs()
      outDir
    }
    // Run QC for each of them and output to a separate dir for each sample.
    val bamAndPassingStatus =
      for (bam <- bams) yield {
        val outDir = createOutputDir(bam)

        //@TODO
        // In the future it should be possible to pass a criterion here, and only if the aligmentQC is good
        // enough the rest of the analysis should go on.
        qscript.add(DepthOfCoverage(bam, outDir))
        (bam, true)
      }

    bamAndPassingStatus
  }

}