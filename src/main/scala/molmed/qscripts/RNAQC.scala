package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.commandline
import collection.JavaConversions._
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.util.QScriptUtils
import molmed.queue.extensions.RNAQC.RNASeQC
import org.broadinstitute.sting.queue.function.InProcessFunction
import scala.io.Source
import java.io.PrintWriter
import molmed.utils.ReadGroupUtils._
import molmed.utils.Uppmaxable
import molmed.utils.GeneralUtils

/**
 * Generate RNA QC metrics and merge the results from the individuals files
 * to a tab separated file with all the results.
 */
class RNAQC extends QScript with Uppmaxable {
  qscript =>

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "input BAM file - or list of BAM files to QC", fullName = "input", shortName = "i", required = true)
  var input: File = _

  @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = true)
  var reference: File = _

  @Input(doc = "GTF File defining the transcripts (must end in .gtf)", shortName = "t", fullName = "transcripts", required = true)
  var transcripts: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = "/usr/bin/samtools"

  @Argument(doc = "Output path for the QC results", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""
  def getOutputDir: String = if (outputDir.isEmpty()) "" else outputDir + "/"

  @Argument(doc = "intervalFile for rRNA loci (must end in .list). This is an alternative flag to the -BWArRNA flag.", shortName = "rRNA", fullName = "rRNA_targets", required = false)
  var rRNATargetsFile: File = _

  @Argument(doc = "Perform downsampling to the given number of reads.", shortName = "d", fullName = "downsample", required = false)
  var downsampling: Int = -1

  @Argument(doc = "The path to RNA-SeQC", shortName = "rnaseqc", fullName = "rna_seqc", required = false)
  var pathToRNASeQC: File = new File("resources/RNA-SeQC_v1.1.7.jar")

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {

    // Get the bam files to analyze
    val bams = QScriptUtils.createSeqFromFile(input)
    
    val generalUtils = new GeneralUtils(projectName, projId, uppmaxQoSFlag)

    // Create output dir if it does not exist
    val outDir = if (getOutputDir == "") new File("RNA_QC") else new File(outputDir)
    outDir.mkdirs()

    val placeHolderFileList = for (bam <- bams) yield {

      val sampleName = getSampleNameFromReadGroups(bam)
      val sampleOutputDir = new File(getOutputDir + sampleName)
      sampleOutputDir.mkdir()

      val index = new File(bam.replace(".bam", ".bai"))
      add(generalUtils.createIndex(bam, index))

      val placeHolderFile = new File(sampleOutputDir + "/qscript_RNASeQC.stdout.log")
      add(RNA_QC(bam, index, reference, sampleOutputDir, transcripts, placeHolderFile, pathToRNASeQC))
      placeHolderFile
    }

    val aggregatedMetrics = new File(getOutputDir + "aggregated_metrics.tsv")
    add(createAggregatedMetrics(placeHolderFileList, outDir, aggregatedMetrics))
  }

  /**
   * **************************************************************************
   * Extension classes
   * **************************************************************************
   */

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {

    val qosFlag = if (!uppmaxQoSFlag.isEmpty) " --qos=" + uppmaxQoSFlag.get else ""
    this.jobNativeArgs +:= "-p node -A " + projId + " " + qosFlag
    this.memoryLimit = 24
    this.isIntermediate = true
  }

  case class RNA_QC(@Input bamfile: File, @Input bamIndex: File, referenceFile: File, outDir: File, transcriptFile: File, placeHolder: File, pathRNASeQC: File) extends RNASeQC with ExternalCommonArgs {

    def createRNASeQCInputString(file: File): String = {
      val sampleName = getSampleNameFromReadGroups(file)
      "\"" + sampleName + "|" + file.getAbsolutePath() + "|" + sampleName + "\""
    }

    val inputString = createRNASeQCInputString(bamfile)

    this.input = inputString
    this.output = outDir
    this.reference = referenceFile
    this.transcripts = transcriptFile
    this.rRNATargetString = if (rRNATargetsFile != null) " -rRNA " + rRNATargetsFile.getAbsolutePath() + " " else ""
    this.downsampleString = if (downsampling > 0) " -d " + downsampling + " " else ""
    this.placeHolderFile = placeHolder
    this.pathToRNASeQC = pathRNASeQC

    this.isIntermediate = false
    this.analysisName = projectName.get + "_RNA_QC"
    this.jobName = projectName.get + "_RNA_QC"
  }

  case class createAggregatedMetrics(@Input placeHolderSeq: Seq[File], @Input outputDir: File, @Output aggregatedMetricsFile: File) extends InProcessFunction {

    def run() = {

      def getFileTree(f: File): Stream[File] =
        f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree)
        else Stream.empty)

      val writer = new PrintWriter(aggregatedMetricsFile)
      val metricsFiles = getFileTree(outputDir).filter(file => file.getName().matches("metrics.tsv"))
      val header = Source.fromFile(metricsFiles(0)).getLines.take(1).next.toString()

      writer.println(header)
      metricsFiles.foreach(file =>
        for (row <- Source.fromFile(file).getLines.drop(1))
          writer.println(row))

      writer.close()

    }
  }
}