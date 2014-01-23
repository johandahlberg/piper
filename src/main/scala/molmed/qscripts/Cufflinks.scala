package molmed.qscripts
import collection.JavaConversions._
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.queue.util.QScriptUtils
import java.io.File
import molmed.utils.ReadGroupUtils
import molmed.utils.UppmaxJob
import molmed.utils.Uppmaxable
import molmed.utils.UppmaxConfig
import molmed.utils.UppmaxXMLConfiguration

/**
 * Run cufflinks on a cohort to generate FPKMs for the known transcripts.
 * It can also use cufflinks novel transcript discovery to find novel
 * transcripts in addition to quantifying known transcripts.
 * However this functionallity should be seen as experimental 
 * at its best.
 */
class Cufflinks extends QScript with UppmaxXMLConfiguration {

  qscript =>

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "input cohort file. One bam file per line.", fullName = "input", shortName = "i", required = true)
  var input: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = false)
  var reference: File = _

  @Input(doc = "The path to the binary of cufflinks", fullName = "path_to_cufflinks", shortName = "cufflinks", required = false)
  var cufflinksPath: File = _

  @Argument(doc = "Output path for the processed files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""
  def getOutputDir: String = if (outputDir.isEmpty()) "" else outputDir + "/"

  @Argument(doc = "Number of threads to use", fullName = "threads", shortName = "nt", required = false)
  var threads: Int = 2

  @Argument(doc = "library type. Options: fr-unstranded (default), fr-firststrand, fr-secondstrand", fullName = "library_type", shortName = "lib", required = false)
  var libraryType: String = "fr-unstranded"

  @Argument(doc = "Annotations of known transcripts in GTF 2.2 or GFF 3 format.", fullName = "annotations", shortName = "a", required = false)
  var annotations: Option[File] = None

  @Argument(doc = "GTF file with transcripts to ignore, e.g. rRNA, mitochondrial transcripts etc.", fullName = "mask", shortName = "m", required = false)
  var maskFile: Option[File] = None

  @Argument(doc = "Use to find novel transcripts. If not will only find transcripts supplied in annotations file.", fullName = "findNovel", shortName = "fn", required = false)
  var findNovelTranscripts: Boolean = false

  @Argument(doc = "Run cuffmerge to merge together the cufflink assemblies.", fullName = "merge", shortName = "me", required = false)
  var merge: Boolean = false

  /**
   * **************************************************************************
   * Private variables
   * **************************************************************************
   */

  def createOutputDir(file: File) = {
    val outDir = {
      val basename = ReadGroupUtils.getSampleNameFromReadGroups(file)
      if (outputDir.isEmpty()) {
        new File("cufflinks/" + basename)
      } else {
        new File(outputDir + "/cufflinks/" + basename)
      }
    }
    outDir.mkdirs()
    outDir
  }

  /**
   * The actual script
   */
  def script {

    val uppmaxConfig = loadUppmaxConfigFromXML()
    
    // final output lists
    var cohortList: Seq[File] = Seq()
    var placeHolderList: Seq[File] = Seq()
    var outputDirList: Seq[File] = Seq()

    val bams = QScriptUtils.createSeqFromFile(input)
        
    val cufflinksUtils = new CufflinksUtils(uppmaxConfig)

    for (bam <- bams) {
      val outDir = createOutputDir(bam)
      val placeHolderFile = new File(outDir + "/qscript_cufflinks.stdout.log")

      add(cufflinksUtils.cufflinks(bam, outDir, placeHolderFile))
      placeHolderList :+= placeHolderFile
      outputDirList :+= outDir
    }

    if (merge) {
      val transcriptList = new File(qscript.outputDir + "cufflinks_transcript.cohort.list")
      add(writeTranscriptList(transcriptList, outputDirList, placeHolderList))

      val placeHolderFile = new File(getOutputDir + "qscript_cuffmerge.stdout.log")
      add(cufflinksUtils.cuffmerge(transcriptList, getOutputDir + "cuffmerge/", reference, placeHolderFile))
    }
  }

  /**
   * Cufflinks utility case classes.
   */
  case class writeTranscriptList(transcriptList: File, outputDirList: Seq[File], placeHolder: Seq[File]) extends ListWriterFunction {

    @Input val ph = placeHolder
    this.listFile = transcriptList
    this.inputFiles = outputDirList.map(file => { file.getAbsolutePath() + "/transcripts.gtf" })
    override def jobRunnerJobName = "writeTranscriptList"

  }

  class CufflinksUtils(uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig) {
    case class cufflinks(inputBamFile: File, sampleOutputDir: File, outputFile: File) extends TwoCoreJob {

      // Sometime this should be kept, sometimes it shouldn't
      this.isIntermediate = false

      @Input var bamFile = inputBamFile
      @Input var dir = sampleOutputDir
      @Output var stdOut = outputFile

      val maskFileString = if (maskFile.isDefined && maskFile.get != null) "--mask-file " + maskFile.get.getAbsolutePath() + " " else ""

      def annotationString = if (annotations.isDefined && annotations.get != null) {
        (if (findNovelTranscripts) " --GTF-guide " else " --GTF ") + annotations.get.getAbsolutePath() + " "
      } else ""

      def commandLine = cufflinksPath + "/cufflinks " +
        " --library-type " + libraryType + " " +
        maskFileString + annotationString +
        " -p " + threads +
        " -o " + sampleOutputDir + " " +
        bamFile + " "
      " 1> " + stdOut

      override def jobRunnerJobName = projectName.get + "_cufflinks"
    }

    case class cuffmerge(assemblies: File, outputDir: File, reference: File, outputFile: File) extends EightCoreJob {

      // Sometime this should be kept, sometimes it shouldn't
      this.isIntermediate = false

      @Input var as = assemblies
      @Input var dir = outputDir
      @Input var ref = reference
      @Output var stdOut = outputFile

      val referenceAnnotationString = if (annotations.isDefined && annotations.get != null)
        " --ref-gtf " + annotations.get.getAbsolutePath() + " "
      else ""

      //cuffmerge -s /seqdata/fastafiles/hg19/hg19.fa assemblies.txt
      def commandLine = cufflinksPath + "/cuffmerge -p " + threads +
        " -o " + dir +
        " --ref-sequence " + ref + " " +
        referenceAnnotationString +
        assemblies +
        " 1> " + stdOut

      override def jobRunnerJobName = projectName.get + "_cuffmerge"
    }
  }
}