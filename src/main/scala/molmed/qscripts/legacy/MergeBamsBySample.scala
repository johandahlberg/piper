package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.util.QScriptUtils
import molmed.utils.ReadGroupUtils._
import molmed.utils.GeneralUtils
import molmed.config.UppmaxXMLConfiguration
import scala.sys.process.Process

/**
 * Merge the bams by the sample names defined by in the read groups.
 * If there is only one file with a specific file name, it will
 * create a hard inlink to the file instead of writing the file again.
 */
class MergeBamsBySample extends QScript with UppmaxXMLConfiguration {

  qscript =>

  @Input(doc = "List of BAM files", fullName = "input", shortName = "i", required = true)
  var input: File = _

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""
  def getOutputDir: String = if (outputDir.isEmpty()) "" else outputDir + "/"

  def script() {

    val bams = QScriptUtils.createSeqFromFile(input)

    val uppmaxConfig = loadUppmaxConfigFromXML()
    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)

    val sampleNamesAndFiles = for (bam <- bams) yield {
      (getSampleNameFromReadGroups(bam), bam)
    }

    val filesGroupedBySampleName =
      sampleNamesAndFiles.
        groupBy(f => f._1).
        mapValues(f => f.map(g => g._2))

    val cohortList =
      for (sampleNamesAndFiles <- filesGroupedBySampleName) yield {

        val sampleName = sampleNamesAndFiles._1
        val mergedFile: File = getOutputDir + sampleName + ".bam"
        val files = sampleNamesAndFiles._2

        // If there is only on file associated with the sample name, just create a
        // hard link instead of merging.
        if (files.size > 1) {
          add(generalUtils.joinBams(files, mergedFile))
          mergedFile
        } else {
          add(createLink(files(0), mergedFile, new File(files(0) + ".bai"), new File(mergedFile + ".bai")))
          mergedFile
        }
      }

    // output a BAM list with all the processed files
    val cohortFile = new File(getOutputDir + "cohort.list")
    add(generalUtils.writeList(cohortList.toSeq, cohortFile))

  }

  case class createLink(@Input inBam: File, @Output outBam: File, @Input index: File, @Output outIndex: File) extends InProcessFunction {

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