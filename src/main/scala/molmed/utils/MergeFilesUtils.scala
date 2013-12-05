package molmed.utils

import molmed.utils.ReadGroupUtils._
import java.io.File
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.commandline.Input
import org.broadinstitute.sting.commandline.Output
import org.broadinstitute.sting.queue.function.InProcessFunction

class MergeFilesUtils(qscript: QScript, projectName: Option[String], uppmaxConfig: UppmaxConfig) extends GeneralUtils(projectName, uppmaxConfig) {

  def mergeFilesBySampleName(sampleNameAndFiles: Map[String, Seq[File]], outputDir: File): Seq[File] = {
    
    val cohortList =
      for (sampleNamesAndFiles <- sampleNameAndFiles) yield {

        val sampleName = sampleNamesAndFiles._1
        val mergedFile: File = new File(outputDir + "/" + sampleName + ".bam")
        val files = sampleNamesAndFiles._2

        // If there is only on file associated with the sample name, just create a
        // hard link instead of merging.
        if (files.size > 1) {
          qscript.add(joinBams(files, mergedFile))
          mergedFile
        } else {
          qscript.add(createLink(files(0), mergedFile, new File(files(0) + ".bai"), new File(mergedFile + ".bai")))
          mergedFile
        }
      }

    cohortList.toSeq
  }
  
  case class createLink(in: File, out: File, ii: File, oi: File) extends InProcessFunction {    
    
    @Input
    var inBam: File = in
    @Output 
    var outBam: File = out
    @Input
    var index: File = ii
    @Output
    var outIndex: File = oi
    
    def run() {

      import scala.sys.process.Process

      def linkProcess(inputFile: File, outputFile: File) =
        Process("""ln """ + inputFile.getAbsolutePath() + """ """ + outputFile.getAbsolutePath())

      // Link index
      val indexExitCode = linkProcess(index, outIndex).!
      assert(indexExitCode == 0, "Couldn't create hard link from: " + index.getAbsolutePath() + " to: " + outIndex.getAbsolutePath())

      // Link bam
      val bamExitCode = linkProcess(inBam, outBam).!
      assert(bamExitCode == 0, "Couldn't create hard link from: " + inBam.getAbsolutePath() + " to: " + outBam.getAbsolutePath())

    }

  }

}