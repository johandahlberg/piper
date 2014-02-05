package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.commandline
import collection.JavaConversions._
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.queue.function.InProcessFunction
import scala.io.Source
import java.io.PrintWriter
import molmed.utils.Uppmaxable
import molmed.utils.GeneralUtils
import molmed.utils.UppmaxConfig
import molmed.utils.UppmaxXMLConfiguration
import molmed.utils.ReadGroupUtils._

/**
 * Generate RNA QC metrics and merge the results from the individuals files
 * to a tab separated file with all the results.
 */
class RNAQC extends QScript with UppmaxXMLConfiguration {
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

  @Input(doc = "BED File defining the transcripts (must end in .bed)", shortName = "tb", fullName = "bed_transcripts", required = true)
  var bedTranscripts: File = _
  
  @Input(doc = "The path to rseqc geneBody_coverage.py", shortName = "gbcp", fullName = "path_to_gene_body_coverage_script", required = true)
  var pathToCalcGeneBodyScript: File = _
  
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

    val uppmaxConfig = loadUppmaxConfigFromXML()
    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)

    // Create output dir if it does not exist
    val outDir = if (getOutputDir == "") new File("RNA_QC") else new File(outputDir)
    outDir.mkdirs()

    val placeHolderFileList = for (bam <- bams) yield {

      val sampleSampleID = getIDFromReadGroups(bam)
      val sampleOutputDir = new File(getOutputDir + sampleSampleID)
      sampleOutputDir.mkdir()

      val index = new File(bam.replace(".bam", ".bai"))
      add(generalUtils.createIndex(bam, index))

      // Run RNA_QC
      val placeHolderFile = new File(sampleOutputDir + "/qscript_RNASeQC.stdout.log")
      add(generalUtils.RNA_QC(bam, index, rRNATargetsFile, downsampling, reference, sampleOutputDir, transcripts, placeHolderFile, pathToRNASeQC))
      
      // Run Gene body coverage calculator
      add(generalUtils.CalculateGeneBodyCoverage(pathToCalcGeneBodyScript, bam, bedTranscripts, sampleSampleID, sampleOutputDir + "/gene_body_coverage/"))
      
      placeHolderFile
    }

    val aggregatedMetrics = new File(getOutputDir + "aggregated_metrics.tsv")
    add(generalUtils.createAggregatedMetrics(placeHolderFileList, outDir, aggregatedMetrics))
  }
}