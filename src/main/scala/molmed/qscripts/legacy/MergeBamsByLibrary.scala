package molmed.qscripts.legacy

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.util.QScriptUtils
import molmed.utils.ReadGroupUtils._
import molmed.utils.GeneralUtils
import molmed.config.UppmaxXMLConfiguration

/**
 * Merge bams by the library specified in the read group.
 */
class MergeBamsByLibrary extends QScript with UppmaxXMLConfiguration {

  qscript =>

  @Input(doc = "List of BAM files", fullName = "input", shortName = "i", required = true)
  var input: File = _

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""
  def getOutputDir: String = if (outputDir.isEmpty()) "" else outputDir + "/"

  case class Sample(library: String, file: File)

  def script() {

    val bams = QScriptUtils.createSeqFromFile(input)

    val uppmaxConfig = loadUppmaxConfigFromXML()
    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)

    val samples = for (bam <- bams) yield {
      new Sample(getLibraryNameFromReadGroups(bam), bam)
    }

    val filesGroupedByLibrary = samples.groupBy(f => f.library).
      mapValues(f => f.map(g => g.file))

    val cohortList =
      for (sampleNamesAndFiles <- filesGroupedByLibrary) yield {

        // Should be same sample name for all libraries.
        val sampleName = getSampleNameFromReadGroups(sampleNamesAndFiles._2(0))
        val mergedFile: File = getOutputDir + sampleName + ".bam"
        val files = sampleNamesAndFiles._2

        add(generalUtils.joinBams(files, mergedFile))
        mergedFile
      }

    // output a BAM list with all the processed files
    val cohortFile = new File(getOutputDir + "cohort.list")
    add(generalUtils.writeList(cohortList.toSeq, cohortFile))

  }
}