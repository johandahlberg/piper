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
import molmed.utils.AlignmentUtils

/**
 * TODO
 * - Fix core/node optimization.
 * 		This cannot be optimized further right now, as the the core programs require more memory. When uppmax upgrades, this will hopefully be possible.
 * - Look at the removing of intermediate bam failes part. Right now it seems that it removes the wrong files when re-running the script.
 */

class AlignWithBWA extends QScript {
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

  @Hidden
  @Argument(doc = "Uppmax qos flag", fullName = "quality_of_service", shortName = "qos", required = false)
  var uppmaxQoSFlag: String = ""
  def getUppmaxQosFlag(): String = if (uppmaxQoSFlag.isEmpty()) "" else " --qos=" + uppmaxQoSFlag

  /**
   * **************************************************************************
   * Private variables
   * **************************************************************************
   */

  var projId: String = ""

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

    val alignmentHelper = new AlignmentUtils(this, bwaPath, bwaThreads, samtoolsPath, projId, getUppmaxQosFlag)
    
    // final output list of bam files
    var cohortList: Seq[File] = samples.values.flatten.map(sample => alignmentHelper.align(sample, outputDir, false)).toSeq

    // output a BAM list with all the processed files
    val cohortFile = new File(qscript.outputDir + setupReader.getProjectName() + ".cohort.list")
    add(writeList(cohortList, cohortFile))
  }

  /**
   * **************************************************************************
   * Case classes - used by qgraph to setup the job run order.
   * **************************************************************************
   */

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {

    this.jobNativeArgs +:= "-p node -A " + projId + " " + getUppmaxQosFlag()
    this.memoryLimit = Some(24)
    this.isIntermediate = false
  }

  case class joinBams(inBams: Seq[File], outBam: File, index: File) extends MergeSamFiles with ExternalCommonArgs {
    this.input = inBams
    this.output = outBam
    this.outputIndex = index

    this.analysisName = "joinBams"
    this.jobName = "joinBams"
    this.isIntermediate = false
  }

  case class writeList(inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    this.analysisName = "bamList"
    this.jobName = "bamList"
  }

  case class sortSam(inSam: File, outBam: File, sortOrderP: SortOrder) extends SortSam with ExternalCommonArgs {
    this.input :+= inSam
    this.output = outBam
    this.sortOrder = sortOrderP
    this.analysisName = "sortSam"
    this.jobName = "sortSam"
  }

  case class removeIntermeditateFiles(@Input files: Seq[File], @Input placeHolder: File) extends InProcessFunction {
    def run(): Unit = {
      files.foreach(f => {
        val success = f.delete()
        if (success)
          logger.debug("Successfully deleted intermediate file: " + f.getAbsoluteFile())
        else
          logger.error("Failed deleted intermediate file: " + f.getAbsoluteFile())
      })
    }
  }

  case class reNameFile(@Input inFile: File, @Output outFile: File) extends InProcessFunction {
    def run(): Unit = {
      inFile.renameTo(outFile)
    }
  }
}