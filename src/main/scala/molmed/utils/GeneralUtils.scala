package molmed.utils

import java.io.File
import java.io.PrintWriter

import scala.annotation.elidable
import scala.annotation.elidable.ASSERTION
import scala.collection.immutable.Stream.consWrapper
import scala.io.Source
import scala.sys.process.Process

import org.broadinstitute.sting.queue.extensions.picard.CalculateHsMetrics
import org.broadinstitute.sting.queue.extensions.picard.MarkDuplicates
import org.broadinstitute.sting.queue.extensions.picard.MergeSamFiles
import org.broadinstitute.sting.queue.extensions.picard.RevertSam
import org.broadinstitute.sting.queue.extensions.picard.SamToFastq
import org.broadinstitute.sting.queue.extensions.picard.SortSam
import org.broadinstitute.sting.queue.extensions.picard.ValidateSamFile
import org.broadinstitute.sting.queue.function.InProcessFunction
import org.broadinstitute.sting.queue.function.ListWriterFunction

import molmed.queue.extensions.RNAQC.RNASeQC
import molmed.queue.extensions.picard.BuildBamIndex
import molmed.queue.extensions.picard.CollectTargetedPcrMetrics
import molmed.queue.extensions.picard.FixMateInformation
import molmed.utils.ReadGroupUtils.getSampleNameFromReadGroups
import net.sf.samtools.SAMFileHeader.SortOrder

/**
 * Assorted commandline wappers, mostly for file doing small things link indexing files. See case classes to figure out
 * what's what.
 */
class GeneralUtils(projectName: Option[String], uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig) {

  /**
   * Creates a bam index for a bam file.
   */
  case class createIndex(@Input bam: File, @Output index: File) extends BuildBamIndex with OneCoreJob {
    this.input = bam
    this.output = index
  }

  /**
   * Joins the bam file specified to a single bam file.
   */
  case class joinBams(@Input inBams: Seq[File], @Output outBam: File, asIntermediate: Boolean = false) extends MergeSamFiles with OneCoreJob {

    this.isIntermediate = asIntermediate

    this.input = inBams
    this.output = outBam

    override def jobRunnerJobName = projectName.get + "_joinBams"

    this.isIntermediate = false
  }

  /**
   * Writes that paths of the inBams to a file, which one file on each line.
   */
  case class writeList(inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    override def jobRunnerJobName = projectName.get + "_bamList"
  }

  /**
   * Sorts a bam file.
   */
  case class sortSam(inSam: File, outBam: File, sortOrderP: SortOrder) extends SortSam with OneCoreJob {
    this.input :+= inSam
    this.output = outBam
    this.sortOrder = sortOrderP
    override def jobRunnerJobName = projectName.get + "_sortSam"
  }

  /**
   * Runs cutadapt on a fastqfile and syncs it (adds a N to any reads which are empty after adaptor trimming).
   */
  case class cutadapt(@Input fastq: File, cutFastq: File, @Argument adaptor: String, @Argument cutadaptPath: String, @Argument syncPath: String = "resources/FixEmptyReads.pl") extends OneCoreJob {

    @Output val fastqCut: File = cutFastq
    this.isIntermediate = true
    // Run cutadapt and sync via perl script by adding N's in all empty reads.  
    def commandLine = cutadaptPath + " -a " + adaptor + " " + fastq + " | perl " + syncPath + " -o " + fastqCut
    override def jobRunnerJobName = projectName.get + "_cutadapt"
  }

  /**
   * Wraps Picard MarkDuplicates
   */
  case class dedup(inBam: File, outBam: File, metricsFile: File, asIntermediate: Boolean = true) extends MarkDuplicates with TwoCoreJob {

    this.isIntermediate = asIntermediate

    this.input :+= inBam
    this.output = outBam
    this.metrics = metricsFile

    // Maximum compression level since we need to write over the network.
    this.compressionLevel = Some(9)

    // Set slightly than maximum lower to make sure it does
    // not die from overflowing the memory limit.
    this.memoryLimit = Some(14)
    override def jobRunnerJobName = projectName.get + "_dedup"
  }

  /**
   * Wraps Picard ValidateSamFile
   */
  case class validate(inBam: File, outLog: File, reference: File) extends ValidateSamFile with OneCoreJob {
    this.input :+= inBam
    this.output = outLog
    this.REFERENCE_SEQUENCE = reference
    this.isIntermediate = false
    override def jobRunnerJobName = projectName.get + "_validate"
  }

  /**
   * Wraps Picard FixMateInformation
   */
  case class fixMatePairs(inBam: Seq[File], outBam: File) extends FixMateInformation with OneCoreJob {
    this.input = inBam
    this.output = outBam
    override def jobRunnerJobName = projectName.get + "_fixMates"
  }

  /**
   * Wraps Picard RevertSam. Removes aligment information from bam file.
   */
  case class revert(inBam: File, outBam: File, removeAlignmentInfo: Boolean) extends RevertSam with OneCoreJob {
    this.output = outBam
    this.input :+= inBam
    this.removeAlignmentInformation = removeAlignmentInfo;
    this.sortOrder = if (removeAlignmentInfo) { SortOrder.queryname } else { SortOrder.coordinate }
    override def jobRunnerJobName = projectName.get + "_revert"

  }

  /**
   * Wraps SamToFastq. Converts a sam file to a fastq file.
   */
  case class convertToFastQ(inBam: File, outFQ: File) extends SamToFastq with OneCoreJob {
    this.input :+= inBam
    this.fastq = outFQ
    override def jobRunnerJobName = projectName.get + "_convert2fastq"
  }

  /**
   * Wrapper for RNA-SeQC.
   */
  case class RNA_QC(@Input bamfile: File, @Input bamIndex: File, rRNATargetsFile: File, downsampling: Int, referenceFile: File, outDir: File, transcriptFile: File, ph: File, pathRNASeQC: File) extends RNASeQC with ThreeCoreJob {

    @Output
    val placeHolder: File = ph

    import molmed.utils.ReadGroupUtils._

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
    override def jobRunnerJobName = projectName.get + "_RNA_QC"
  }

  /**
   * InProcessFunction which searches a file tree for files matching metrics.tsv
   * (the output files from RNA-SeQC) and create a file containing the results
   * from all the separate runs.
   *
   */
  case class createAggregatedMetrics(phs: Seq[File], @Input var outputDir: File, @Output var aggregatedMetricsFile: File) extends InProcessFunction {

    @Input
    val placeHolderSeq: Seq[File] = phs

    def run() = {

      val writer = new PrintWriter(aggregatedMetricsFile)
      val metricsFiles = GeneralUtils.getFileTree(outputDir).filter(file => file.getName().matches("metrics.tsv"))
      val header = Source.fromFile(metricsFiles(0)).getLines.take(1).next.toString()

      writer.println(header)
      metricsFiles.foreach(file =>
        for (row <- Source.fromFile(file).getLines.drop(1))
          writer.println(row))

      writer.close()

    }
  }

  /**
   * Run Picards CollectTargetedPcrMetrics
   */
  case class collectTargetedPCRMetrics(bam: File, generalStatisticsOutput: File, perTargetStat: File,
                                       baitIntervalFile: File, targetIntervalFile: File, ref: File)
      extends CollectTargetedPcrMetrics with OneCoreJob {

    this.isIntermediate = false

    this.input = Seq(bam)
    this.output = generalStatisticsOutput
    this.perTargetOutputFile = perTargetStat
    this.amplicons = baitIntervalFile
    this.targets = targetIntervalFile
    this.reference = ref

    override def jobRunnerJobName = projectName.get + "_collectPCRMetrics"

  }

  /**
   * Run Picards CalculateHsMetrics
   */
  case class calculateHsMetrics(@Input bam: File, @Input baitsToUse: Option[File],
                                @Input targetsToUse: Option[File], @Output outputMetrics: File,
                                @Input referenceFile: File)
      extends CalculateHsMetrics with OneCoreJob {

    this.input = Seq(bam)
    this.output = outputMetrics

    this.baits = baitsToUse.getOrElse(throw new IllegalArgumentException("Didn't find a bait file."))
    this.targets = targetsToUse.getOrElse(throw new IllegalArgumentException("Didn't find a target/interval file."))

    this.reference = referenceFile

    override def jobRunnerJobName = projectName.get + "_collectHSMetrics"

  }
}

/**
 * Contains some general utility functions. See each description.
 */
object GeneralUtils {

  /**
   * Exchanges the extension on a file.
   * @param file File to look for the extension.
   * @param oldExtension Old extension to strip off, if present.
   * @param newExtension New extension to append.
   * @return new File with the new extension in the current directory.
   */
  def swapExt(file: File, oldExtension: String, newExtension: String) =
    new File(file.getName.stripSuffix(oldExtension) + newExtension)

  /**
   * Exchanges the extension on a file.
   * @param dir New directory for the file.
   * @param file File to look for the extension.
   * @param oldExtension Old extension to strip off, if present.
   * @param newExtension New extension to append.
   * @return new File with the new extension in dir.
   */
  def swapExt(dir: File, file: File, oldExtension: String, newExtension: String) =
    new File(dir, file.getName.stripSuffix(oldExtension) + newExtension)

  /**
   * Check that all the files that make up bwa index exist for the reference.
   */
  def checkReferenceIsBwaIndexed(reference: File): Unit = {
    assert(reference.exists(), "Could not find reference.")

    val referenceBasePath: String = reference.getAbsolutePath()
    for (fileEnding <- Seq("amb", "ann", "bwt", "pac", "sa")) {
      assert(new File(referenceBasePath + "." + fileEnding).exists(), "Could not find index file with file ending: " + fileEnding)
    }
  }

  /**
   * Returns the Int with zero padding to the desired length.
   */
  def getZerroPaddedIntAsString(i: Int, totalStringLength: Int): String = {

    def rep(n: Int)(f: => String): String = {
      if (n == 1)
        f
      else
        f + rep(n - 1)(f)
    }

    rep(totalStringLength - i.toString().length()) { "0" } + i
  }

  /**
   * Get file tree starting from file
   * @param file root to get file tree from
   * @return the file tree starting from f
   */
  def getFileTree(f: File): Stream[File] =
    f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree)
    else Stream.empty)

  /**
   * Create a hard link using the linux cp command
   * Will overwrite destination file if it exists!
   * @param inputFile	file to link
   * @param	outputFile	path to hardlink
   * @param	withWildcard	Should the path end with a * or not.
   * @return the exit status of the process.
   */
  def linkProcess(inputFile: File, outputFile: File, withWildcard: Boolean = false): Int = {
    import scala.sys.process.Process    
    
    def addWildCard: String = if (withWildcard) "*" else ""
    def stripToParentFile: String =
      if (withWildcard)
        outputFile.getParent() + "/"
      else
        outputFile.getAbsolutePath()

    val processString =
      "sh -c \"" +
      """cp --recursive --force --link """ +
        inputFile.getAbsolutePath() +
        addWildCard +
        """ """ +
        stripToParentFile +
        "\""

    val exitCode = Process(processString).!

    assert(exitCode == 0,
      "Exit status: " + exitCode + " Couldn't create hard link with command: " + processString)

    exitCode
  }

}