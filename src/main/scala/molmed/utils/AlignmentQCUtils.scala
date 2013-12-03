package molmed.utils

import java.io.File
import org.broadinstitute.sting.queue.QScript

class AlignmentQCUtils(qscript: QScript, gatkOptions: GATKOptions, projectName: Option[String], uppmaxConfig: UppmaxConfig)
  extends GATKUtils(gatkOptions, projectName, uppmaxConfig) {

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