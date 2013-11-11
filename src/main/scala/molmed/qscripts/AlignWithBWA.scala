package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import scala.xml._
import collection.JavaConversions._
import net.sf.samtools.SAMFileReader
import net.sf.samtools.SAMFileHeader.SortOrder
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.commandline.Hidden
import molmed.queue.setup._
import org.broadinstitute.sting.queue.function.InProcessFunction
import org.broadinstitute.sting.utils.io.IOUtils
import molmed.utils.GeneralUtils._
import molmed.utils.BwaAlignmentUtils
import molmed.utils.Uppmaxable
import molmed.utils.GeneralUtils

/**
 * 
 * Perform aligment of singel or paired end reads using BWA. 
 * 
 */

class AlignWithBWA extends QScript with Uppmaxable {
  qscript =>

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "input pipeline setup xml", fullName = "input", shortName = "i", required = true)
  var input: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName = "path_to_bwa", shortName = "bwa", required = false)
  var bwaPath: File = _

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = "/usr/bin/samtools"

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "Number of threads BWA should use", fullName = "bwa_threads", shortName = "bt", required = false)
  var bwaThreads: Int = 1

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {

    // Load the setup
    val setupReader: SetupXMLReaderAPI = new SetupXMLReader(input)

    val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
    projId = setupReader.getUppmaxProjectId()
    uppmaxQoSFlag = setupReader.getUppmaxQoSFlag()
    projectName = setupReader.getProjectName()

    val alignmentHelper = new BwaAlignmentUtils(this, bwaPath, bwaThreads, samtoolsPath, projectName, projId, uppmaxQoSFlag)
    val generalUtils = new GeneralUtils(projectName, projId, uppmaxQoSFlag)
    
    // final output list of bam files
    var cohortList: Seq[File] = samples.values.flatten.map(sample => alignmentHelper.align(sample, outputDir, false)).toSeq

    // output a BAM list with all the processed files
    val cohortFile = new File(qscript.outputDir + setupReader.getProjectName() + ".cohort.list")
    add(generalUtils.writeList(cohortList, cohortFile))
  }

}