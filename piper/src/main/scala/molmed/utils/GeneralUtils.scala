package molmed.utils

import java.io.File
import java.io.PrintWriter

import scala.collection.immutable.Stream.consWrapper
import scala.io.Source
import scala.sys.process.Process

import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.picard.CalculateHsMetrics
import org.broadinstitute.gatk.queue.extensions.picard.MergeSamFiles
import org.broadinstitute.gatk.queue.extensions.picard.RevertSam
import org.broadinstitute.gatk.queue.extensions.picard.SamToFastq
import org.broadinstitute.gatk.queue.extensions.picard.SortSam
import org.broadinstitute.gatk.queue.extensions.picard.ValidateSamFile
import org.broadinstitute.gatk.queue.function.InProcessFunction
import org.broadinstitute.gatk.queue.function.ListWriterFunction
import org.broadinstitute.gatk.queue.util.StringFileConversions
import org.broadinstitute.gatk.tools.walkers.bqsr.BQSRGatherer


import htsjdk.samtools.SAMFileHeader.SortOrder
import molmed.queue.extensions.RNAQC.RNASeQC
import molmed.queue.extensions.picard.BuildBamIndex
import molmed.queue.extensions.picard.CollectTargetedPcrMetrics
import molmed.queue.extensions.picard.FixMateInformation
import molmed.queue.extensions.picard.MarkDuplicates
import molmed.queue.extensions.picard.MarkDuplicatesMetrics
import molmed.queue.setup.ReadPairContainer
import molmed.queue.setup.Sample
import molmed.queue.setup.SampleAPI

/**
 * Assorted commandline wappers, mostly for file doing small things link indexing files. See case classes to figure out
 * what's what.
 */
class GeneralUtils(projectName: Option[String], uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig) with StringFileConversions { 
  
  /**
   * Creates a bam index for a bam file.
   */
  case class createIndex(@Input bam: File, @Output index: File) extends BuildBamIndex with OneCoreJob {
    this.input = bam
    this.output = index
  }

  abstract class SamtoolsBase(@Argument samtoolsPath: String)
  
  /**
   * Creates a bam index for a bam file.
   */
  case class samtoolCreateIndex(@Input bam: File, @Output index: File, @Argument samtoolsPath: String) extends SamtoolsBase(samtoolsPath) with OneCoreJob {
    def commandLine = samtoolsPath + " index " + bam + " " + index + "; echo \"ExitCode: \"$?";

    override def jobRunnerJobName = projectName.get + "_samtools_bam_index"
  }

  /**
   * Get only reads from a bam file mapping to the specified region
   * 
   * @param bam             The bam to get the reads from
   * @param outputBam       The bam file to output the reads to
   * @param region          The region to select (e.g. chr1) note that this must match region in file.
   * @param asIntermediate  Remove once dependencies has been fullfilled
   */
  case class samtoolGetRegion(
      @Input bam: File, 
      @Output outputBam: File, 
      @Argument region: String, 
      @Argument asIntermediate: Boolean,
      @Argument samtoolsPath: String) extends SamtoolsBase(samtoolsPath) with OneCoreJob {

    @Output
    var outputBamIndex: File = GeneralUtils.swapExt(outputBam.getParentFile(), outputBam, ".bam", ".bam.bai")

    def commandLine =
      samtoolsPath + " view -b " + bam + " " + region + " > " + outputBam + "; " +
      samtoolsPath + " index " + outputBam
    override def jobRunnerJobName = projectName.get + "_samtools_get_region"
    this.isIntermediate = asIntermediate
  }

  /**
   * Joins the bam file specified to a single bam file.
   */
  case class joinBams(inBams: Seq[File], outBam: File, asIntermediate: Boolean = false) extends MergeSamFiles with TwoCoreJob {

    this.isIntermediate = asIntermediate

    this.input = inBams
    this.output = outBam

    this.USE_THREADING = true
    // 5 seems to be a good compromise between speed and file size
    this.compressionLevel = Some(5)

    override def jobRunnerJobName = projectName.get + "_joinBams"

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
   *
   * Read trimmer
   *
   */
  def cutSamplesUsingCuteAdapt(qscript: QScript, cutadaptPath: String, sample: SampleAPI, outputDir: String, @Argument syncPath: String = "resources/FixEmptyReads.pl"): SampleAPI = {
    // Standard Illumina adaptors
    val adaptor1 = "AGATCGGAAGAGCACACGTCTGAACTCCAGTCAC"
    val adaptor2 = "AGATCGGAAGAGCGTCGTGTAGGGAAAGAGTGTAGATCTCGGTGGTCGCCGTATCATT"

    val cutadaptOutputDir = new File(outputDir + "/cutadapt")
    cutadaptOutputDir.mkdirs()

    // Run cutadapt & sync    
    def cutAndSyncSample(sample: SampleAPI): SampleAPI = {
      def constructTrimmedName(name: String): String = {
        if (name.endsWith("fastq.gz"))
          name.replace("fastq.gz", "trimmed.fastq.gz")
        else
          name.replace("fastq", "trimmed.fastq.gz")
      }

      val readpairContainer = sample.getFastqs

      val mate1SyncedFastq = new File(cutadaptOutputDir + "/" + sample.getReadGroupInformation.platformUnitId + "/" + constructTrimmedName(sample.getFastqs.mate1.getName()))
      qscript.add(this.cutadapt(readpairContainer.mate1, mate1SyncedFastq, adaptor1, cutadaptPath, syncPath))

      val mate2SyncedFastq = if (readpairContainer.isMatePaired) {
        val mate2SyncedFastq = new File(cutadaptOutputDir + "/" + sample.getReadGroupInformation.platformUnitId + "/" + constructTrimmedName(sample.getFastqs.mate2.getName()))
        qscript.add(this.cutadapt(readpairContainer.mate2, mate2SyncedFastq, adaptor2, cutadaptPath, syncPath))
        mate2SyncedFastq
      } else null

      val readGroupContainer = new ReadPairContainer(mate1SyncedFastq, mate2SyncedFastq, sample.getSampleName)

      new Sample(sample.getSampleName, sample.getReference, sample.getReadGroupInformation, readGroupContainer)
    }

    cutAndSyncSample(sample)
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
    this.outputIndex = GeneralUtils.swapExt(outBam.getParentFile, outBam, ".bam", ".bai")
    this.metrics = metricsFile

    // 5 seems to be a good compromise between speed and file size
    this.compressionLevel = Some(5)

    // Set slightly than maximum lower to make sure it does
    // not die from overflowing the memory limit.
    this.memoryLimit = Some(14)
    override def jobRunnerJobName = projectName.get + "_dedup"
  }

  case class dedupMetrics(inBam: File, metricsFile: File) extends MarkDuplicatesMetrics with TwoCoreJob {

    this.isIntermediate = false
    this.input = Seq(inBam)
    this.metrics = metricsFile
    // Since we're discarding the ouptut, set the compression level to be low
    this.compressionLevel = Some(1)
    this.memoryLimit = Some(14)
    override def jobRunnerJobName = projectName.get + "_dedup_metrics"
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
  case class RNA_QC(bamfile: File, @Input bamIndex: File, bamSampleName: String, rRNATargetsFile: File, downsampling: Int, referenceFile: File, outDir: File, transcriptFile: File, @Output placeHolder: File, pathRNASeQC: File) extends RNASeQC with ThreeCoreJob {
    //Perform QC on bam files

    def createRNASeQCInputString(file: File): String = {
      val sampleName = bamSampleName
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
   * Run qualimap to get info quality control metrics
   * @param bam 			file to run qualimap on
   * @param outputBase 		the folder where the output files will end up
   * @param logFile 		the path to output the log file to
   * @param pathToQualimap  path to the qualimap program
   * @param isHuman			indicate if gc-content should be compared to human distribution or not.
   * @param skipDup use flag --skip-duplicated, 0 : only flagged duplicates, 1 : only estimated by Qualimap, 2 : both flagged and estimated
   * @param intervalFile	Optional interval file in BED or GFF format to
   * 						output statistics for targeted regions.
   */
  case class qualimapQC(
    @Input bam: File,
    @Output outputBase: File,
    @Output logFile: File,
    @Argument pathToQualimap: File,
    @Argument isHuman: Boolean,
    @Argument skipDup: Int,
    @Argument(required = false) intervalFile: Option[File] = None)
      extends SixteenCoreJob {

    this.isIntermediate = false
    override def jobRunnerJobName = projectName.get + "_qualimap"

    def gffString =
      if (intervalFile.isDefined)
        " -gff " + intervalFile.get.getAbsolutePath() + " "
      else
        ""

    def compareGCString =
      if (isHuman)
        "--genome-gc-distr HUMAN"
      else
        ""

    def skiplDuplicated =
      if(skipDup != -1)
        "--skip-duplicated --skip-dup-mode " + skipDup
      else
        ""

    override def commandLine =
      pathToQualimap + " " +
        " --java-mem-size=" + this.memoryLimit.get.toInt.toString + "G " +
        " bamqc " +
        " -bam " + bam.getAbsolutePath() +
        gffString +
        " --paint-chromosome-limits " +
        " " + compareGCString + " " +
        " -outdir " + outputBase.getAbsolutePath() + "/" +
        " -nt " + this.coreLimit.toInt.toString +
        " " + skiplDuplicated + " " +
        " &> " + logFile.getAbsolutePath()

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

  /**
   * Merge BQSR recalibration tables
   *
   * @param inRecalFiles Seq of recalibration table files to merge
   * @param outRecalFile the merged output recalibration table file
   */
  case class mergeRecalibrationTables(@Input inRecalFiles: Seq[File], @Output outRecalFile: File, asIntermediate: Boolean = false) extends InProcessFunction {
    import scala.collection.JavaConverters._

    this.isIntermediate = asIntermediate

    def run() {
      new BQSRGatherer().gather(inRecalFiles.toList.asJava, outRecalFile)
    }

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
    f #:: (
      if (f.isDirectory)
        f.listFiles().toStream.flatMap(getFileTree)
      else
        Stream.empty)

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

    val command =
      "cp --recursive --force --link " +
        inputFile.getAbsolutePath() + addWildCard + " " +
        stripToParentFile

    val processCommand =
      Seq("sh", "-c",
        command)

    val exitCode = Process(processCommand).!

    assert(exitCode == 0,
      "Exit status: " + exitCode + " Couldn't create hard link with command: " + processCommand)

    exitCode
  }

}