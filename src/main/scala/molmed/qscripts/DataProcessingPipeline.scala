package molmed.qscripts

import java.io.FileNotFoundException
import scala.collection.JavaConversions._
import org.broadinstitute.sting.commandline.Hidden
import org.broadinstitute.sting.gatk.walkers.indels.IndelRealigner.ConsensusDeterminationModel
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.extensions.picard._
import molmed.queue.extensions.picard.FixMateInformation
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.utils.baq.BAQ.CalculationMode
import net.sf.picard.reference.IndexedFastaSequenceFile
import net.sf.samtools.SAMFileHeader.SortOrder
import net.sf.samtools.SAMFileReader
import molmed.utils.Uppmaxable
import molmed.utils.GATKUtils
import molmed.utils.GATKOptions
import molmed.utils.GeneralUtils

/**
 * Runs the GATK recommended best practice analysis for data processing.
 * Base quality recalibration, indel realignment, etc.
 *
 * @TODO
 * - Update to new best practice.
 * - Look at node/core optimization.
 * - Might want to include reduce bam, etc. in this to increase the effectivity.
 */

class DataProcessingPipeline extends QScript with Uppmaxable {
  qscript =>

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "input BAM file - or list of BAM files", fullName = "input", shortName = "i", required = true)
  var input: File = _

  @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = true)
  var reference: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "dbsnp ROD to use (must be in VCF format)", fullName = "dbsnp", shortName = "D", required = false)
  var dbSNP: Seq[File] = Seq()

  @Input(doc = "extra VCF files to use as reference indels for Indel Realignment", fullName = "extra_indels", shortName = "indels", required = false)
  var indels: Seq[File] = Seq()

  @Input(doc = "The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName = "path_to_bwa", shortName = "bwa", required = false)
  var bwaPath: File = _

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "the -L interval string to be used by GATK - output bams at interval only", fullName = "gatk_interval_string", shortName = "L", required = false)
  var intervalString: String = ""

  @Input(doc = "an intervals file to be used by GATK - output bams at intervals only", fullName = "gatk_interval_file", shortName = "intervals", required = false)
  var intervals: File = _

  @Argument(doc = "Cleaning model: KNOWNS_ONLY, USE_READS or USE_SW", fullName = "clean_model", shortName = "cm", required = false)
  var cleaningModel: String = "USE_READS"

  @Argument(doc = "Realign the bam file", fullName = "realign", shortName = "ra", required = false)
  var realign: Boolean = false

  @Argument(doc = "Decompose input BAM file and fully realign it using BWA and assume Single Ended reads", fullName = "use_bwa_single_ended", shortName = "bwase", required = false)
  var useBWAse: Boolean = false

  @Argument(doc = "Decompose input BAM file and fully realign it using BWA and assume Pair Ended reads", fullName = "use_bwa_pair_ended", shortName = "bwape", required = false)
  var useBWApe: Boolean = false

  @Argument(doc = "Decompose input BAM file and fully realign it using BWA SW", fullName = "use_bwa_sw", shortName = "bwasw", required = false)
  var useBWAsw: Boolean = false

  @Argument(doc = "Number of threads BWA should use", fullName = "bwa_threads", shortName = "bt", required = false)
  var bwaThreads: Int = 1

  @Argument(doc = "Perform validation on the BAM files", fullName = "validation", shortName = "vs", required = false)
  var validation: Boolean = false

  @Argument(doc = "Number of threads to use in thread enabled walkers. Default: 1", fullName = "nbr_of_threads", shortName = "nt", required = false)
  var nbrOfThreads: Int = 1

  @Argument(doc = "Remove old alignment/base recal/duplicate info from input bams before realigning.", fullName = "revert", shortName = "rev", required = false)
  var revertBams: Boolean = false

  @Argument(doc = "Explicitly fix the mate pairs after alignement. This option seems to be needed when running on manually split files.", fullName = "fixMatePairInformation", shortName = "fixMateInfo", required = false)
  var fixMatePairInfo: Boolean = false

  @Argument(doc = "Skip the deduplication step - usefull when runing for example haloplex data", fullName = "skipDeduplication", shortName = "sdd", required = false)
  var skipDeduplication: Boolean = false

  /**
   * **************************************************************************
   * Hidden Parameters
   * **************************************************************************
   */
  @Hidden
  @Argument(doc = "How many ways to scatter/gather", fullName = "scatter_gather", shortName = "sg", required = false)
  var nContigs: Int = -1

  @Hidden
  @Argument(doc = "Define the default platform for Count Covariates -- useful for techdev purposes only.", fullName = "default_platform", shortName = "dp", required = false)
  var defaultPlatform: String = ""

  @Hidden
  @Argument(doc = "Run the pipeline in test mode only", fullName = "test_mode", shortName = "test", required = false)
  var testMode: Boolean = false

  /**
   * **************************************************************************
   * Global Variables
   * **************************************************************************
   */

  val queueLogDir: String = ".qlog/" // Gracefully hide Queue's output
  var cleanModelEnum: ConsensusDeterminationModel = ConsensusDeterminationModel.USE_READS

  /**
   * **************************************************************************
   * Helper classes and methods
   * **************************************************************************
   */

  class ReadGroup(val id: String,
    val lb: String,
    val pl: String,
    val pu: String,
    val sm: String,
    val cn: String,
    val ds: String) {}

  // Utility function to merge all bam files of similar samples. Generates one BAM file per sample.
  // It uses the sample information on the header of the input BAM files.
  //
  // Because the realignment only happens after these scripts are executed, in case you are using
  // bwa realignment, this function will operate over the original bam files and output over the
  // (to be realigned) bam files.
  def createSampleFiles(bamFiles: Seq[File], realignedBamFiles: Seq[File]): Map[String, Seq[File]] = {

    // Creating a table with SAMPLE information from each input BAM file
    val sampleTable = scala.collection.mutable.Map.empty[String, Seq[File]]
    val realignedIterator = realignedBamFiles.iterator
    for (bam <- bamFiles) {
      val rBam = realignedIterator.next() // advance to next element in the realignedBam list so they're in sync.

      val samReader = new SAMFileReader(bam)
      val header = samReader.getFileHeader
      val readGroups = header.getReadGroups

      if (readGroups.isEmpty()) throw new RuntimeException("The bam file: " + bam + "does not contain a read group header. Please add one.")

      // only allow one sample per file. Bam files with multiple samples would require pre-processing of the file
      // with PrintReads to separate the samples. Tell user to do it himself!
      assert(!QScriptUtils.hasMultipleSamples(readGroups), "The pipeline requires that only one sample is present in a BAM file. Please separate the samples in " + bam)

      // Fill out the sample table with the readgroups in this file
      for (rg <- readGroups) {
        val sample = rg.getSample
        if (!sampleTable.contains(sample))
          sampleTable(sample) = Seq(rBam)
        else if (!sampleTable(sample).contains(rBam))
          sampleTable(sample) :+= rBam
      }
    }
    sampleTable.toMap
  }

  // Rebuilds the Read Group string to give BWA
  def addReadGroups(inBam: File, outBam: File, samReader: SAMFileReader) {
    val readGroups = samReader.getFileHeader.getReadGroups
    var index: Int = readGroups.length
    for (rg <- readGroups) {
      val intermediateInBam: File = if (index == readGroups.length) { inBam } else { swapExt(outBam, ".bam", index + 1 + "-rg.bam") }
      val intermediateOutBam: File = if (index > 1) { swapExt(outBam, ".bam", index + "-rg.bam") } else { outBam }
      val readGroup = new ReadGroup(rg.getReadGroupId, rg.getLibrary, rg.getPlatform, rg.getPlatformUnit, rg.getSample, rg.getSequencingCenter, rg.getDescription)
      add(addReadGroup(intermediateInBam, intermediateOutBam, readGroup))
      index = index - 1
    }
  }

  // Takes a list of processed BAM files and realign them using the BWA option requested  (bwase or bwape).
  // Returns a list of realigned BAM files.
  def performAlignment(bams: Seq[File], generalUtils: GeneralUtils): Seq[File] = {
    var realignedBams: Seq[File] = Seq()
    var index = 1
    for (bam <- bams) {
      // first revert the BAM file to the original qualities
      val saiFile1 = swapExt(bam, ".bam", "." + index + ".1.sai")
      val saiFile2 = swapExt(bam, ".bam", "." + index + ".2.sai")
      val realignedSamFile = swapExt(bam, ".bam", "." + index + ".realigned.sam")
      val realignedBamFile = swapExt(bam, ".bam", "." + index + ".realigned.bam")
      val rgRealignedBamFile = swapExt(bam, ".bam", "." + index + ".realigned.rg.bam")

      val runBAM = if (revertBams) revertBAM(bam, true, generalUtils) else bam

      if (useBWAse) {

        add(bwa_aln_se(runBAM, saiFile1),
          bwa_sam_se(runBAM, saiFile1, realignedSamFile))
      } else if (useBWApe) {
        add(bwa_aln_pe(runBAM, saiFile1, 1),
          bwa_aln_pe(runBAM, saiFile2, 2),
          bwa_sam_pe(runBAM, saiFile1, saiFile2, realignedSamFile))
      } else if (useBWAsw) {
        val fastQ = swapExt(runBAM, ".bam", ".fq")
        add(generalUtils.convertToFastQ(runBAM, fastQ),
          bwa_sw(fastQ, realignedSamFile))
      }
      add(generalUtils.sortSam(realignedSamFile, realignedBamFile, SortOrder.coordinate))
      addReadGroups(realignedBamFile, rgRealignedBamFile, new SAMFileReader(bam))
      realignedBams :+= rgRealignedBamFile
      index = index + 1
    }
    realignedBams
  }

  def getIndelCleaningModel: ConsensusDeterminationModel = {
    if (cleaningModel == "KNOWNS_ONLY")
      ConsensusDeterminationModel.KNOWNS_ONLY
    else if (cleaningModel == "USE_SW")
      ConsensusDeterminationModel.USE_SW
    else
      ConsensusDeterminationModel.USE_READS
  }

  def revertBams(bams: Seq[File], removeAlignmentInformation: Boolean, generalUtils: GeneralUtils): Seq[File] = {
    var revertedBAMList: Seq[File] = Seq()
    for (bam <- bams)
      revertedBAMList :+= revertBAM(bam, removeAlignmentInformation, generalUtils)
    revertedBAMList
  }

  def revertBAM(bam: File, removeAlignmentInformation: Boolean, generalUtils: GeneralUtils): File = {
    val revertedBAM = swapExt(bam, ".bam", ".reverted.bam")
    add(generalUtils.revert(bam, revertedBAM, removeAlignmentInformation))
    revertedBAM
  }

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {

    // Setup the scatter/gather counts.
    if (nContigs < 0)
    {
      val fastaFileIndex = new IndexedFastaSequenceFile(reference)
      val seqDictionary = fastaFileIndex.getSequenceDictionary()
      nContigs = if (seqDictionary != null)
        scala.math.min(seqDictionary.size(), 23)
      else
        throw new FileNotFoundException("Could not find a dict file for the reference: " + reference.toString + ". Make one using picards: CreateSequenceDictionary")
    }

    // Setup of options utility classes.
    val gatkOptions = {
      implicit def file2Option(file: File) = if (file == null) None else Some(file)
      implicit def seqfile2Option(seq: Seq[File]) = if (seq == null) None else Some(seq)
      new GATKOptions(reference, nbrOfThreads, nContigs, intervals, dbSNP, indels)
    }
    
    val gatkUtils = new GATKUtils(gatkOptions, projectName, projId, uppmaxQoSFlag)
    val generalUtils = new GeneralUtils(projectName, projId, uppmaxQoSFlag)

    // final output list of processed bam files
    var cohortList: Seq[File] = Seq()

    // sets the model for the Indel Realigner
    cleanModelEnum = getIndelCleaningModel

    // keep a record of the number of contigs in the reference.
    val bams = QScriptUtils.createSeqFromFile(input)

    // In some cases it might be necessary to realign samples from the bam files. This will
    // handle this.
    val realignedBAMs = if (realign) { performAlignment(bams, generalUtils) } else { bams }

    // generate a BAM file per sample joining all per lane files if necessary
    val sampleBAMFiles: Map[String, Seq[File]] = createSampleFiles(bams, realignedBAMs)

    // if this is a 'knowns only' indel realignment run, do it only once for all samples.
    val globalIntervals = new File(outputDir + projectName.get + ".intervals")
    if (cleaningModel == ConsensusDeterminationModel.KNOWNS_ONLY)
      add(gatkUtils.target(null, globalIntervals, cleanModelEnum))

    // put each sample through the pipeline
    for ((sample, bamList) <- sampleBAMFiles) {

      // BAM files generated by the pipeline      
      val bam = if (outputDir.isEmpty())
        new File(projectName.get + "." + sample)
      else
        new File(outputDir + projectName.get + "." + sample)

      // When running on manually split files, it seems that the mate pairs need
      // to be fixed explicitly
      val fixBamList =
        if (fixMatePairInfo) {
          add(generalUtils.fixMatePairs(bamList, bam))
          Seq(bam)
        } else
          bamList

      val cleanedBam = swapExt(bam, ".bam", ".clean.bam")
      val dedupedBam = swapExt(bam, ".bam", ".clean.dedup.bam")
      val recalBam = if (!skipDeduplication) swapExt(bam, ".bam", ".clean.dedup.recal.bam") else swapExt(bam, ".bam", ".clean.recal.bam")

      // Accessory files
      val targetIntervals = if (cleaningModel == ConsensusDeterminationModel.KNOWNS_ONLY) { globalIntervals } else { swapExt(bam, ".bam", ".intervals") }
      val metricsFile = swapExt(bam, ".bam", ".metrics")
      val preRecalFile = swapExt(bam, ".bam", ".pre_recal.table")
      val postRecalFile = swapExt(bam, ".bam", ".post_recal.table")
      val preOutPath = swapExt(bam, ".bam", ".pre")
      val postOutPath = swapExt(bam, ".bam", ".post")
      val preValidateLog = swapExt(bam, ".bam", ".pre.validation")
      val postValidateLog = swapExt(bam, ".bam", ".post.validation")

      // Validation is an optional step for the BAM file generated after
      // alignment and the final bam file of the pipeline.
      if (validation) {
        for (sampleFile <- fixBamList)
          add(generalUtils.validate(sampleFile, preValidateLog, reference),
            generalUtils.validate(recalBam, postValidateLog, reference))
      }

      if (cleaningModel != ConsensusDeterminationModel.KNOWNS_ONLY)
        add(gatkUtils.target(fixBamList, targetIntervals, cleanModelEnum))

      add(gatkUtils.clean(fixBamList, targetIntervals, cleanedBam, cleanModelEnum, testMode))

      if (!skipDeduplication)
        add(generalUtils.dedup(cleanedBam, dedupedBam, metricsFile),
          gatkUtils.cov(dedupedBam, preRecalFile, defaultPlatform),
          gatkUtils.recal(dedupedBam, preRecalFile, recalBam),
          gatkUtils.cov(recalBam, postRecalFile, defaultPlatform))
      else
        add(gatkUtils.cov(cleanedBam, preRecalFile, defaultPlatform),
          gatkUtils.recal(cleanedBam, preRecalFile, recalBam),
          gatkUtils.cov(recalBam, postRecalFile, defaultPlatform))

      cohortList :+= recalBam
    }

    // output a BAM list with all the processed per sample files
    val cohortFile = new File(qscript.outputDir + projectName.get + ".cohort.list")
    add(generalUtils.writeList(cohortList, cohortFile))
  }

  // Override the normal swapExt metod by adding the outputDir to the file path by default if it is defined.
  override def swapExt(file: File, oldExtension: String, newExtension: String) = {
    if (outputDir.isEmpty())
      new File(file.getName.stripSuffix(oldExtension) + newExtension)
    else
      swapExt(outputDir, file, oldExtension, newExtension);
  }

  /**
   * **************************************************************************
   * Classes (GATK Walkers)
   * **************************************************************************
   */

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {
    this.memoryLimit = 24
    this.isIntermediate = true
  }

  // General arguments to GATK walkers
  trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {
    this.reference_sequence = qscript.reference
  }

  trait SAMargs extends PicardBamFunction with ExternalCommonArgs {
    this.maxRecordsInRam = 100000
  }

  /**
   * **************************************************************************
   * Classes (non-GATK programs)
   * Most of these are custom bwa case classes which handle realigment of a
   * bam file rather than aligning from fastq input data.
   * **************************************************************************
   */

  case class addReadGroup(inBam: File, outBam: File, readGroup: ReadGroup) extends AddOrReplaceReadGroups with ExternalCommonArgs {
    this.input :+= inBam
    this.output = outBam
    this.RGID = readGroup.id
    this.RGCN = readGroup.cn
    this.RGDS = readGroup.ds
    this.RGLB = readGroup.lb
    this.RGPL = readGroup.pl
    this.RGPU = readGroup.pu
    this.RGSM = readGroup.sm
    override def jobRunnerJobName = projectName.get + "_addRG"   
  }

  case class bwa_aln_se(inBam: File, outSai: File) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc = "bam file to be aligned") var bam = inBam
    @Output(doc = "output sai file") var sai = outSai
    def commandLine = bwaPath + " aln -t " + bwaThreads + " -q 5 " + reference + " -b " + bam + " > " + sai
    override def jobRunnerJobName = queueLogDir + outSai + ".bwa_aln_se"
    
  }

  case class bwa_aln_pe(inBam: File, outSai1: File, index: Int) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc = "bam file to be aligned") var bam = inBam
    @Output(doc = "output sai file for 1st mating pair") var sai = outSai1
    def commandLine = bwaPath + " aln -t " + bwaThreads + " -q 5 " + reference + " -b" + index + " " + bam + " > " + sai
    override def jobRunnerJobName = queueLogDir + outSai1 + ".bwa_aln_pe1"
    
  }

  case class bwa_sam_se(inBam: File, inSai: File, outBam: File) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc = "bam file to be aligned") var bam = inBam
    @Input(doc = "bwa alignment index file") var sai = inSai
    @Output(doc = "output aligned bam file") var alignedBam = outBam
    def commandLine = bwaPath + " samse " + reference + " " + sai + " " + bam + " > " + alignedBam
    override def jobRunnerJobName = queueLogDir + outBam + ".bwa_sam_se"
    
  }

  case class bwa_sam_pe(inBam: File, inSai1: File, inSai2: File, outBam: File) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc = "bam file to be aligned") var bam = inBam
    @Input(doc = "bwa alignment index file for 1st mating pair") var sai1 = inSai1
    @Input(doc = "bwa alignment index file for 2nd mating pair") var sai2 = inSai2
    @Output(doc = "output aligned bam file") var alignedBam = outBam
    def commandLine = bwaPath + " sampe " + reference + " " + sai1 + " " + sai2 + " " + bam + " " + bam + " > " + alignedBam
    override def jobRunnerJobName = queueLogDir + outBam + ".bwa_sam_pe"    
  }

  case class bwa_sw(inFastQ: File, outBam: File) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc = "fastq file to be aligned") var fq = inFastQ
    @Output(doc = "output bam file") var bam = outBam
    def commandLine = bwaPath + " bwasw -t " + bwaThreads + " " + reference + " " + fq + " > " + bam
    override def jobRunnerJobName = queueLogDir + outBam + ".bwasw"
    
  }
}
