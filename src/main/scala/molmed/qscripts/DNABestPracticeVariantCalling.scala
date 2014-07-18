package molmed.qscripts

import org.broadinstitute.gatk.queue.QScript
import molmed.queue.setup.SampleAPI
import molmed.utils.AlignerOption
import molmed.utils.AlignmentQCUtils
import molmed.utils.BwaAlignmentUtils
import molmed.utils.BwaAln
import molmed.utils.BwaMem
import molmed.utils.GATKConfig
import molmed.utils.GATKDataProcessingUtils
import molmed.utils.GATKHaplotypeCaller
import molmed.utils.GATKUnifiedGenotyper
import molmed.utils.GeneralUtils
import molmed.utils.MergeFilesUtils
import molmed.utils.UppmaxConfig
import molmed.config.UppmaxXMLConfiguration
import molmed.utils.VariantCallerOption
import molmed.utils.VariantCallingConfig
import molmed.utils.VariantCallingUtils
import org.broadinstitute.gatk.queue.function.InProcessFunction
import java.io.PrintWriter
import molmed.utils.DeliveryUtils
import molmed.config.FileAndProgramResourceConfig
import org.broadinstitute.gatk.utils.commandline.Hidden

/**
 *
 * Run broads recommended pipeline for DNA variant calling:
 *
 *  Should work for both exomes and whole genomes.
 *
 */

class DNABestPracticeVariantCalling extends QScript
    with UppmaxXMLConfiguration
    with FileAndProgramResourceConfig {

  // qscript will now be a alias for this (QScript)
  qscript =>

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "an intervals file to be used by GATK. (In Picard interval format)", fullName = "gatk_interval_file", shortName = "intervals", required = false)
  var intervals: File = _

  @Input(doc = "a baits file in Picard interval format format. Used to calculate HSMetrics for exomes.", fullName = "baits_file", shortName = "baits", required = false)
  var baits: File = _

  @Input(doc = "an intervals file to be used by qualimap. (In BED/GFF format)", fullName = "bed_interval_file", shortName = "bed_intervals", required = false)
  var bedIntervals: File = _

  @Argument(doc = "Cleaning model: KNOWNS_ONLY, USE_READS or USE_SW. (Default: USE_READS)", fullName = "clean_model", shortName = "cm", required = false)
  var cleaningModel: String = "USE_READS"

  @Argument(doc = "The type of bwa aligner to use. Options are BWA_MEM and BWA_ALN. (Default: BWA_MEM)", fullName = "bwa_aligner", shortName = "bwaa", required = false)
  var bwaAlignerType: String = "BWA_MEM"

  @Argument(doc = "Base path for all output working files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = "pipeline_output"

  @Argument(doc = "Base path for final delivery folder structure.", fullName = "delivery_directory", shortName = "deliveryDir", required = false)
  var deliveryDir: String = "pipeline_output/delivery"

  @Argument(doc = "Number of threads to use by default", fullName = "number_of_threads", shortName = "nt", required = false)
  var nbrOfThreads: Int = 1

  @Argument(doc = "How many ways to scatter/gather. (Default: 1)", fullName = "scatter_gather", shortName = "sg", required = false)
  var scatterGatherCount: Int = 1

  @Argument(doc = "If the project is a non-human project - which means that there are normally no resources available.", fullName = "not_human", shortName = "nh", required = false)
  var notHuman: Boolean = false

  @Argument(doc = "If the project is a low pass project. - Used by variant calling.", fullName = "lowpass", shortName = "lp", required = false)
  var isLowPass: Boolean = false

  @Argument(doc = "If the project is a exome sequencing project", fullName = "isExome", shortName = "ie", required = false)
  var isExome: Boolean = false

  @Argument(doc = "Run variant calling for each bam file seperatly. By default all samples will be analyzed together", fullName = "analyze_separatly", shortName = "analyzeSeparatly", required = false)
  var runSeparatly = false

  @Argument(shortName = "noBAQ", doc = "turns off BAQ calculation in variant calling", required = false)
  var noBAQ: Boolean = false

  @Argument(shortName = "noIndels", doc = "do not call indels with the Unified Genotyper", required = false)
  var noIndels: Boolean = false

  @Argument(fullName = "skip_recalibration", shortName = "noRecal", doc = "Skip recalibration of variants", required = false)
  var noRecal: Boolean = false

  @Argument(shortName = "mbq", doc = "The minimum Phred-Scaled quality score threshold to be considered a good base in variant calling", required = false)
  var minimumBaseQuality: Int = -1

  @Argument(shortName = "deletions", doc = "Maximum deletion fraction allowed at a site to call a genotype in variant calling", required = false)
  var deletions: Double = -1

  @Argument(doc = "Downsample fraction of coverage in variant calling. [0.0 - 1.0]", fullName = "downsample_to_fraction", shortName = "dtf", required = false)
  var downsampleFraction: Double = -1

  @Argument(doc = "Remove the raw merged alignment files.", fullName = "remove_raw_merged_alignments", shortName = "rrma", required = false)
  var removeMergedAlignments: Boolean = false

  @Argument(doc = "Indicate if the libraries was prepared using a PCR free library or not.", fullName = "pcr_free_libraries", shortName = "pcrfree", required = false)
  var pcrFreeLibrary: Boolean = false

  @Argument(doc = "Choose which variant caller to use. Options are: HaplotypeCaller, UnifiedGenotyper", fullName = "variant_caller", shortName = "vc", required = false)
  var variantCaller: String = "HaplotypeCaller"

  @Argument(doc = "Do the aligments and initial quality control.", fullName = "alignment_and_qc", shortName = "doaqc", required = false)
  var doAlignmentAndQualityControl: Boolean = true

  @Argument(doc = "Merge the samples based on their names.", fullName = "merge_alignments", shortName = "dma", required = false)
  var doMergeSamples: Boolean = false

  @Argument(doc = "Run GATK data processing.", fullName = "data_processing", shortName = "ddp", required = false)
  var doDataProcessing: Boolean = false

  @Argument(doc = "Run variant calling.", fullName = "variant_calling", shortName = "dvc", required = false)
  var doVariantCalling: Boolean = false

  @Argument(doc = "Create the final delivery output structure (and report).", fullName = "create_delivery", shortName = "cdlvry", required = false)
  var doGenerateDelivery: Boolean = false

  /**
   * **************************************************************************
   * Hidden Parameters - for dev.
   * **************************************************************************
   */

  @Hidden
  @Argument(doc = "Run the pipeline in test mode only", fullName = "test_mode", shortName = "test", required = false)
  var testMode: Boolean = false

  /**
   * **************************************************************************
   * Helper functions
   * **************************************************************************
   */

  /**
   * Implicitly convert any File to Option File, as necessary.
   */
  implicit def file2Option(file: File) = if (file == null) None else Some(file)

  /**
   * Deparces string options into proper Variant caller options
   * @param stringOption	Text to convert to Option class
   * @returns A Option[Aligner] holding a valid aligner option
   */
  def decideVariantCallerType(stringOption: String): Option[VariantCallerOption] = {
    stringOption match {
      case "HaplotypeCaller"  => Some(GATKHaplotypeCaller)
      case "UnifiedGenotyper" => Some(GATKUnifiedGenotyper)
      case s: String          => throw new IllegalArgumentException("Did not recognize variant caller option: " + s)
    }
  }

  /**
   * Deparces string options into proper Aligner options
   * @param stringOption	Text to convert to Option class
   * @returns A Option[Aligner] holding a valid aligner option
   */
  def decideAlignerType(stringOption: String): Option[AlignerOption] = {
    stringOption match {
      case "BWA_MEM" => Some(BwaMem)
      case "BWA_ALN" => Some(BwaAln)
      case s: String => throw new IllegalArgumentException("Did not recognize aligner option: " + s)
    }
  }

  /**
   * Run alignments
   */
  def runAlignments(samples: Map[String, Seq[SampleAPI]],
                    uppmaxConfig: UppmaxConfig,
                    alignmentOutputDir: File): Map[String, Seq[File]] = {

    val aligner: Option[AlignerOption] = decideAlignerType(bwaAlignerType)
    val alignmentUtils = new BwaAlignmentUtils(this, bwaPath, nbrOfThreads, samtoolsPath, projectName, uppmaxConfig)
    val sampleNamesAndalignedBamFiles = samples.values.flatten.map(sample =>
      (sample.getSampleName,
        alignmentUtils.align(sample, alignmentOutputDir, asIntermidate = !doAlignmentAndQualityControl, aligner)))
    val sampleNamesToBamMap = sampleNamesAndalignedBamFiles.groupBy(f => f._1).mapValues(f => f.map(x => x._2).toSeq)
    sampleNamesToBamMap

  }

  /**
   * Merge by sample
   */
  def runMergeBySample(sampleNamesToBamMap: Map[String, Seq[java.io.File]],
                       uppmaxConfig: UppmaxConfig,
                       mergedAligmentOutputDir: File): Seq[File] = {

    val mergeFilesUtils = new MergeFilesUtils(this, projectName, uppmaxConfig)
    val mergedBamFiles = mergeFilesUtils.mergeFilesBySampleName(sampleNamesToBamMap,
      mergedAligmentOutputDir, asIntermediate = removeMergedAlignments)
    mergedBamFiles

  }

  /**
   * Get QC statistics
   */
  def runQualityControl(
    bamFiles: Seq[File],
    intervalsToUse: Option[File],
    reference: File,
    aligmentQCOutputDir: File,
    generalUtils: GeneralUtils): Seq[File] = {

    val qualityControlUtils = new AlignmentQCUtils(qscript, projectName, generalUtils, qualimapPath)
    val baseQCOutputFiles = qualityControlUtils.aligmentQC(bamFiles, aligmentQCOutputDir, bedIntervals)

    /**
     * For exomes, calculate hybrid selection metrics.
     */
    if (isExome && baits.isDefined) {
      for (bam <- bamFiles) {
        val outputMetrics: File = swapExt(aligmentQCOutputDir, bam, ".bam", ".hs.metrics.txt")
        add(generalUtils.calculateHsMetrics(bam, baits,
          intervalsToUse, outputMetrics, reference))
      }
    }

    baseQCOutputFiles

  }

  /**
   * Data processing
   */
  def runDataProcessing(
    bams: Seq[File],
    processedAligmentsOutputDir: File,
    gatkOptions: GATKConfig,
    generalUtils: GeneralUtils,
    uppmaxConfig: UppmaxConfig): Seq[File] = {

    val gatkDataProcessingUtils = new GATKDataProcessingUtils(
      this, gatkOptions, generalUtils, projectName, uppmaxConfig)
    val processedBamFiles = gatkDataProcessingUtils.dataProcessing(
      bams, processedAligmentsOutputDir, cleaningModel,
      skipDeduplication = false, testMode)
    processedBamFiles

  }

  /**
   * Variant calling
   */
  def runVariantCalling(
    bamFiles: Seq[File],
    outputDirectory: File,
    gatkOptions: GATKConfig,
    uppmaxConfig: UppmaxConfig): Seq[File] = {

    val variantCallerToUse: Option[VariantCallerOption] = decideVariantCallerType(variantCaller)

    val variantCallingUtils = new VariantCallingUtils(gatkOptions, projectName, uppmaxConfig)
    val variantCallingConfig = new VariantCallingConfig(
      qscript = this,
      variantCaller = variantCallerToUse,
      bamFiles,
      outputDirectory,
      runSeparatly,
      notHuman,
      isLowPass,
      isExome,
      noRecal,
      noIndels,
      testMode,
      downsampleFraction,
      minimumBaseQuality,
      deletions,
      noBAQ,
      Some(pcrFreeLibrary))

    variantCallingUtils.performVariantCalling(variantCallingConfig)
  }

  /**
   * Create a folders structure ready for delivery
   */
  def runCreateDelivery(
    samples: Seq[SampleAPI],
    processedBamFile: Seq[File],
    qualityControlDir: Seq[File],
    variantCallFiles: Seq[File],
    deliveryDirectory: File): Unit = {

    add(DeliveryUtils.SetupDeliveryStructure(
      samples, processedBamFile,
      qualityControlDir, variantCallFiles,
      deliveryDirectory))
  }

  /**
   * Define possible steps in workflow
   */
  object AnalysisSteps extends Enumeration {
    type AnalysisSteps = Value
    val Alignment, QualityControl, MergePerSample, DataProcessing, VariantCalling, GenerateDelivery = Value
  }

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {

    /**
     * Defining output dirs for the different parts of the run
     */

    val alignmentOutputDir: File = new File(outputDir + "/01_raw_alignments")
    alignmentOutputDir.mkdirs()
    val preliminaryAlignmentQCOutputDir: File = new File(outputDir + "/02_preliminary_alignment_qc")
    preliminaryAlignmentQCOutputDir.mkdirs()
    val mergedAligmentOutputDir: File = new File(outputDir + "/03_merged_aligments")
    mergedAligmentOutputDir.mkdirs()
    val processedAligmentsOutputDir: File = new File(outputDir + "/04_processed_alignments")
    processedAligmentsOutputDir.mkdirs()
    val finalAlignmentQCOutputDir: File = new File(outputDir + "/05_final_alignment_qc")
    finalAlignmentQCOutputDir.mkdirs()
    val variantCallsOutputDir: File = new File(outputDir + "/06_variant_calls")
    variantCallsOutputDir.mkdirs()
    val miscOutputDir: File = new File(outputDir + "/07_misc")
    miscOutputDir.mkdirs()

    /**
     * Setup of resources to use
     */
    val uppmaxConfig = loadUppmaxConfigFromXML(testMode = qscript.testMode)
    val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
    // NOTE: assumes all samples are to be aligned to the same reference.
    val reference = samples.head._2(0).getReference()

    // Get default paths to resources from global config xml
    if (this.globalConfig.isDefined)
      this.setResourcesFromConfigXML(this.globalConfig)

    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)

    val gatkOptions =
      new GATKConfig(reference, nbrOfThreads, scatterGatherCount,
        intervals,
        dbSNP, Some(indels), hapmap, omni, mills, thousandGenomes)

    /**
     * Define a number of partial functions which will then be chained
     * together depending on which parts of the workflow are to be run.
     */
    val alignments =
      runAlignments(
        _: Map[String, Seq[SampleAPI]],
        uppmaxConfig,
        alignmentOutputDir)

    val mergedAlignments =
      runMergeBySample(
        _: Map[String, Seq[File]],
        uppmaxConfig,
        mergedAligmentOutputDir)

    val qualityControl = runQualityControl(
      _: Seq[File], intervals, reference,
      _: File, generalUtils)

    val dataProcessing = runDataProcessing(
      _: Seq[File], processedAligmentsOutputDir,
      gatkOptions, generalUtils, uppmaxConfig)

    val variantCalling = runVariantCalling(
      _: Seq[File], variantCallsOutputDir,
      gatkOptions, uppmaxConfig)

    /**
     *  Defined the workflow to run
     */
    val analysisStepsToRun: List[AnalysisSteps.Value] =
      if (doGenerateDelivery)
        List(AnalysisSteps.GenerateDelivery)
      else if (doVariantCalling)
        List(AnalysisSteps.VariantCalling)
      else if (doDataProcessing)
        List(AnalysisSteps.DataProcessing)
      else if (doMergeSamples)
        List(AnalysisSteps.MergePerSample)
      else
        List(AnalysisSteps.Alignment, AnalysisSteps.QualityControl)

    /**
     * Run the different parts depending on what parts have
     * been included in the list.
     */
    analysisStepsToRun match {
      case List(AnalysisSteps.Alignment, AnalysisSteps.QualityControl) => {
        qualityControl(
          alignments(samples).values.flatten.toSeq, preliminaryAlignmentQCOutputDir)
      }
      case e if e.contains(AnalysisSteps.MergePerSample) => {
        val aligments = alignments(samples)
        val qc = qualityControl(aligments.values.flatten.toSeq, preliminaryAlignmentQCOutputDir)
        val mergedBams = mergedAlignments(aligments)
      }
      case e if e.contains(AnalysisSteps.DataProcessing) => {
        val aligments = alignments(samples)
        val qc = qualityControl(aligments.values.flatten.toSeq, preliminaryAlignmentQCOutputDir)
        val mergedBams = mergedAlignments(aligments)
        val processedBams = dataProcessing(mergedBams)
        qualityControl(processedBams, finalAlignmentQCOutputDir)
      }
      case e if e.contains(AnalysisSteps.VariantCalling) => {
        val aligments = alignments(samples)
        val qc = qualityControl(aligments.values.flatten.toSeq, preliminaryAlignmentQCOutputDir)
        val mergedBams = mergedAlignments(aligments)
        val processedBams = dataProcessing(mergedBams)
        qualityControl(processedBams, finalAlignmentQCOutputDir)
        variantCalling(processedBams)
      }
      case e if e.contains(AnalysisSteps.GenerateDelivery) => {

        val fastqs =
          samples.
            flatMap(x => x._2).
            toSeq

        val aligments = alignments(samples)
        val preliminaryQC = qualityControl(aligments.values.flatten.toSeq, preliminaryAlignmentQCOutputDir)
        val mergedBams = mergedAlignments(aligments)
        val processedBams = dataProcessing(mergedBams)
        val finalQC = qualityControl(processedBams, finalAlignmentQCOutputDir)
        val variantCallFiles = variantCalling(processedBams)

        runCreateDelivery(fastqs, processedBams, finalQC, variantCallFiles, deliveryDir)
      }

    }
  }
}
