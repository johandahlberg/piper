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
import molmed.utils.BamUtils._

class RNAQC extends QScript {
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

  @Argument(doc = "UPPMAX project id", fullName = "project_id", shortName = "pid", required = false)
  var projId: String = _

  @Argument(doc = "Output path for the QC results", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "intervalFile for rRNA loci (must end in .list). This is an alternative flag to the -BWArRNA flag.", shortName = "rRNA", fullName = "rRNA_targets", required = false)
  var rRNATargetsFile: Option[File] = None

  @Argument(doc = "Perform downsampling to the given number of reads.", shortName = "d", fullName = "downsample", required = false)
  var downsampling: Option[Int] = None

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {

    // Get the bam files to analyze
    val bams = QScriptUtils.createSeqFromFile(input)

    // Create output dir if it does not exist
    val outDir = if (outputDir == "") new File("RNA_QC") else new File(outputDir)
    outDir.mkdirs()

    val placeHolderFileList = for (bam <- bams) yield {
      val sampleName = getSampleNameFromReadGroups(bam)
      val sampleOutputDir = new File(outDir + "/" + sampleName)
      sampleOutputDir.mkdir()
      val placeHolderFile = new File(sampleOutputDir + "/qscript_RNASeQC.stdout.log")
      add(RNA_QC(bam, reference, sampleOutputDir, transcripts, placeHolderFile))
      placeHolderFile
    }

    val aggregatedMetrics = new File(outDir + "/" + "aggregated_metrics.tsv")
    add(createAggregatedMetrics(placeHolderFileList, outDir, aggregatedMetrics))
  }

  /**
   * **************************************************************************
   * Extension classes
   * **************************************************************************
   */

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {
    this.memoryLimit = 24
    this.isIntermediate = true
    this.jobNativeArgs +:= "-p node -A " + projId
  }

  case class RNA_QC(@Input bamfile: File, referenceFile: File, outDir: File, transcriptFile: File, placeHolder: File) extends RNASeQC with ExternalCommonArgs {

    def createRNASeQCInputString(file: File): String = {
      val sampleName = getSampleNameFromReadGroups(file)
      "\"" + sampleName + "|" + file.getAbsolutePath() + "|" + sampleName + "\""
    }

    val inputString = createRNASeQCInputString(bamfile)

    this.input = inputString
    this.output = outDir
    this.reference = referenceFile
    this.transcripts = transcriptFile
    this.rRNATargets = rRNATargetsFile
    this.downsample = downsampling
    this.placeHolderFile = placeHolder

    this.isIntermediate = false
    this.analysisName = "RNA_QC"
    this.jobName = "RNA_QC"
  }

  case class createAggregatedMetrics(@Input placeHolderSeq: Seq[File], @Input outputDir: File, @Output aggregatedMetricsFile: File) extends InProcessFunction {

    def run() = {

      def findFiles(path: File, fileFilter: PartialFunction[File, Boolean] = { case _ => false }): List[File] = {
        (path :: path.listFiles.toList.filter {
          _.isDirectory
        }.flatMap {
          findFiles(_)
        }).filter(fileFilter.isDefinedAt(_))
      }

      val aggregatedMetrics = new File(aggregatedMetricsFile)
      val writer = new PrintWriter(aggregatedMetrics)

      val metricsFiles = findFiles(outputDir, { case file: File => file.getName().endsWith(".metrics.tsv") })
      val header = Source.fromFile(metricsFiles(0)).getLines.take(1).toString

      writer.write(header)
      metricsFiles.foreach(file => writer.write(Source.fromFile(file).getLines.drop(1).toString))

      writer.close()

    }
  }
}