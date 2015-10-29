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
import molmed.utils.DeliveryUtils
import molmed.config.FileAndProgramResourceConfig
import org.broadinstitute.gatk.utils.commandline.Hidden
import molmed.report.ReportGenerator
import molmed.config.FileVersionUtilities.ResourceMap
import molmed.utils.SplitFilesAndMergeByChromosome

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

  @Input(doc = "a file with genotyping results (in vcf format). This is optional and is used to check genotype concordance.", fullName = "genotypes", shortName = "gt", required = false)
  var snpGenotypes: File = _

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

  @Argument(doc = "Run variant calling for each bam file separately. By default all samples will be analyzed together", fullName = "analyze_separately", shortName = "analyzeSeparately", required = false)
  var runSeparatly = false

  @Argument(shortName = "noBAQ", doc = "turns off BAQ calculation in variant calling", required = false)
  var noBAQ: Boolean = false

  @Argument(shortName = "noIndels", doc = "do not call indels with the Unified Genotyper", required = false)
  var noIndels: Boolean = false

  @Argument(fullName = "skip_recalibration", shortName = "noRecal", doc = "Skip recalibration of variants", required = false)
  var noRecal: Boolean = false

  @Argument(fullName = "skip_annotation", shortName = "noAnnotation", doc = "Skip the snpEff annotation step", required = false)
  var skipAnnotation: Boolean = false

  @Argument(fullName = "skip_vcf_compression", shortName = "noCompress", doc = "Skip gz compression of vcf files", required = false)
  var skipVcfCompression: Boolean = false

  @Argument(shortName = "mbq", doc = "The minimum Phred-Scaled quality score threshold to be considered a good base in variant calling", required = false)
  var minimumBaseQuality: Int = -1

  @Argument(shortName = "deletions", doc = "Maximum deletion fraction allowed at a site to call a genotype in variant calling", required = false)
  var deletions: Double = -1

  @Argument(doc = "Downsample fraction of coverage in variant calling. [0.0 - 1.0]", fullName = "downsample_to_fraction", shortName = "dtf", required = false)
  var downsampleFraction: Double = -1

  @Argument(doc = "Indicate if the libraries was prepared using a PCR free library or not.", fullName = "pcr_free_libraries", shortName = "pcrfree", required = false)
  var pcrFreeLibrary: Boolean = false

  @Argument(doc = "Choose which variant caller to use. Options are: HaplotypeCaller, UnifiedGenotyper", fullName = "variant_caller", shortName = "vc", required = false)
  var variantCaller: String = "HaplotypeCaller"

  @Argument(doc = "Do the aligments and initial quality control.", fullName = "alignment_and_qc", shortName = "doaqc", required = false)
  var doAlignmentAndQualityControl: Boolean = false

  @Argument(doc = "Merge the samples based on their names.", fullName = "merge_alignments", shortName = "dma", required = false)
  var doMergeSamples: Boolean = false

  @Argument(doc = "Run GATK data processing.", fullName = "data_processing", shortName = "ddp", required = false)
  var doDataProcessing: Boolean = false

  @Argument(doc = "Run variant calling.", fullName = "variant_calling", shortName = "dvc", required = false)
  var doVariantCalling: Boolean = false

  @Argument(doc = "Create the final delivery output structure (and report).", fullName = "create_delivery", shortName = "cdlvry", required = false)
  var doGenerateDelivery: Boolean = false

  @Argument(doc = "Super charge single node analysis explicitly splitting on chromosome, this is mostly useful when using the ParallelShell job runner", fullName = "super_charge", shortName = "sc", required = false)
  var useExplicitChromosomeSplit: Boolean = false

  @Argument(doc = "When using the --super_charge option, use this to specify number of groups (default: 3)", fullName = "ways_to_split", shortName = "wts", required = false)
  var groupsToSplitTo: Int = 3

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
        alignmentUtils.align(
          sample,
          alignmentOutputDir,
          asIntermidate = doMergeSamples || doDataProcessing || doVariantCalling || doGenerateDelivery,
          aligner)))
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
      mergedAligmentOutputDir, asIntermediate = doDataProcessing || doVariantCalling || doGenerateDelivery)
    mergedBamFiles

  }

  /**
   * Get QC statistics
   */
  def runQualityControl(
    bamFiles: Seq[File],
    intervalsToUse: Option[File],
    snpGenotypes: Option[File],
    reference: File,
    aligmentQCOutputDir: File,
    genotypeConcordanceOutputDir: File,
    generalUtils: GeneralUtils,
    gatkOptions: GATKConfig,
    uppmaxConfig: UppmaxConfig): Seq[File] = {

    val qualityControlUtils = new AlignmentQCUtils(qscript, projectName, generalUtils, qualimapPath)
    val baseQCOutputFiles = qualityControlUtils.aligmentQC(bamFiles, aligmentQCOutputDir, !notHuman, bedIntervals)

    if (snpGenotypes.isDefined) {
      qualityControlUtils.checkGenotypeConcordance(
        bams = bamFiles,
        outputBase = genotypeConcordanceOutputDir,
        comparisonVcf = snpGenotypes.get,
        qscript = this,
        gatkOptions = gatkOptions,
        projectName = this.projectName,
        uppmaxConfig = uppmaxConfig,
        isLowPass = this.isLowPass,
        isExome = this.isExome,
        testMode = this.testMode,
        minimumBaseQuality = this.minimumBaseQuality,
        skipVcfCompression = this.skipVcfCompression)
    }

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
   * Split into chromosome chunks (used to speed up single node analysis)
   */
  def runChromosomeSplitting(
    bams: Seq[File],
    waysToSplit: Int,
    generalUtils: GeneralUtils,
    reference: File): Seq[Seq[File]] = {

    val nameOfDictFile = reference.getName().stripSuffix(".fasta") + ".dict"

    val inputFastaDict =
      reference.getParentFile().listFiles().
        find(file => file.getName() == nameOfDictFile).
        getOrElse(throw new Exception(s"Couldn't find $nameOfDictFile, please make " +
          s"sure to place such a file in the same directory as ${reference.getName()}."))

    for (bam <- bams) yield {
      val splitBams =
        SplitFilesAndMergeByChromosome.splitByChromosome(
          this,
          bam,
          inputFastaDict,
          waysToSplit,
          generalUtils,
          asIntermediate = false,
          samtoolsPath)
      splitBams
    }
  }

  /**
   * Data processing
   */
  def runDataProcessing(
    bams: Seq[File],
    processedAligmentsOutputDir: File,
    gatkOptions: GATKConfig,
    generalUtils: GeneralUtils,
    uppmaxConfig: UppmaxConfig,
    reference: File): Seq[File] = {

    /**
     * Used internally to handle splitting, processing and merging.
     */
    def runDataProcessingOnSplitByChromosomeAndMerge = {

      val updateGATKOptions = gatkOptions.copy(nbrOfThreads = 16 / groupsToSplitTo)
      
      val gatkDataProcessingUtils = new GATKDataProcessingUtils(
        this, gatkOptions, generalUtils, projectName, uppmaxConfig)

      val splitsBams = runChromosomeSplitting(bams, groupsToSplitTo, generalUtils, reference)

      val splitAndProcessedBams =
        for (splitGroup <- splitsBams) yield {
          val processedBamFiles = gatkDataProcessingUtils.dataProcessing(
            splitGroup, processedAligmentsOutputDir, cleaningModel,
            skipDeduplication = false, testMode)
          processedBamFiles
        }

      for (toMergeBams <- splitAndProcessedBams) yield {

        // Assumes that the start of the file name is the same, and is what is to
        // be used name these files.
        val nameOfOutputBam =
          if (toMergeBams.size > 1) {
            val firstFileName = toMergeBams(0).getName()
            val secondFileName = toMergeBams(1).getName()
            val longestCommonName =
              firstFileName.zip(secondFileName).takeWhile(Function.tupled(_ == _)).map(_._1).mkString
            // The splitting will add a _, removing it here.  
            longestCommonName.stripSuffix("_")
          } else
            toMergeBams(0).getName().stripSuffix(".bam")

        val outBam = new File(processedAligmentsOutputDir + "/" + nameOfOutputBam + ".bam")
        SplitFilesAndMergeByChromosome.merge(qscript, toMergeBams, outBam, asIntermediate = false, generalUtils)
      }
    }

    // The function body starts here!

    if (useExplicitChromosomeSplit) {
      runDataProcessingOnSplitByChromosomeAndMerge
    } else {
      val gatkDataProcessingUtils = new GATKDataProcessingUtils(
        this, gatkOptions, generalUtils, projectName, uppmaxConfig)

      gatkDataProcessingUtils.dataProcessing(
        bams, processedAligmentsOutputDir, cleaningModel,
        skipDeduplication = false, testMode)
    }
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

    val updatedGatkOptions =
      if (useExplicitChromosomeSplit)
        gatkOptions.copy(scatterGatherCount = 4, nbrOfThreads = 4)
      else
        gatkOptions

    val variantCallingUtils = new VariantCallingUtils(updatedGatkOptions, projectName, uppmaxConfig)
    val variantCallingConfig = new VariantCallingConfig(
      qscript = this,
      variantCaller = variantCallerToUse,
      bamFiles,
      outputDirectory,
      runSeparatly,
      isLowPass,
      isExome,
      noRecal,
      noIndels,
      testMode,
      downsampleFraction,
      minimumBaseQuality,
      deletions,
      noBAQ,
      Some(pcrFreeLibrary),
      snpEffPath,
      snpEffConfigPath,
      Some(snpEffReference),
      skipAnnotation,
      skipVcfCompression)

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
    reportFile: File,
    deliveryDirectory: File): Unit = {

    add(DeliveryUtils.SetupDeliveryStructure(
      samples, processedBamFile,
      qualityControlDir, variantCallFiles,
      reportFile,
      deliveryDirectory))
  }

  /**
   * Generate the report file containing the versions of input files used
   * and similar information.
   * @param resourceMap
   * @param reference
   * @param reportFile
   * @return The report file that has been written to.
   */
  def createReport(resourceMap: ResourceMap, reference: File, reportFile: File): File = {
    ReportGenerator.
      constructDNABestPracticeVariantCallingReport(
        resourceMap,
        reference,
        reportFile)
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
    val genotypeConcordanceOutputDir: File = new File(outputDir + "/03_genotype_concordance")
    genotypeConcordanceOutputDir.mkdirs()
    val mergedAligmentOutputDir: File = new File(outputDir + "/04_merged_aligments")
    mergedAligmentOutputDir.mkdirs()
    val processedAligmentsOutputDir: File = new File(outputDir + "/05_processed_alignments")
    processedAligmentsOutputDir.mkdirs()
    val finalAlignmentQCOutputDir: File = new File(outputDir + "/06_final_alignment_qc")
    finalAlignmentQCOutputDir.mkdirs()
    val variantCallsOutputDir: File = new File(outputDir + "/07_variant_calls")
    variantCallsOutputDir.mkdirs()
    val miscOutputDir: File = new File(outputDir + "/08_misc")
    miscOutputDir.mkdirs()
    val logs: File = new File(outputDir + "/logs")
    logs.mkdirs()

    // The file to which to write the program versions etc. 
    val reportFile = new File(logs + "/version_report.txt")

    /**
     * Setup of resources to use
     */
    val uppmaxConfig = loadUppmaxConfigFromXML(testMode = qscript.testMode)
    val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
    // NOTE: assumes all samples are to be aligned to the same reference.
    val reference = samples.head._2(0).getReference()

    // Get default paths to resources from global config xml
    val resourceMap =
      this.configureResourcesFromConfigXML(this.globalConfig, notHuman)

    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)

    val gatkOptions =
      new GATKConfig(reference, nbrOfThreads, scatterGatherCount,
        intervals,
        dbSNP, Some(indels), hapmap, omni, mills, thousandGenomes,
        notHuman)

    // Drop the version report (this will be overwritten each time the 
    // qscript is run.
    createReport(resourceMap, reference, reportFile)

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
      _: Seq[File],
      intervals,
      snpGenotypes,
      reference,
      _: File,
      genotypeConcordanceOutputDir,
      generalUtils,
      gatkOptions,
      uppmaxConfig)

    val dataProcessing =
      runDataProcessing(
        _: Seq[File], processedAligmentsOutputDir,
        gatkOptions, generalUtils, uppmaxConfig, reference)

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

        runCreateDelivery(
          fastqs,
          processedBams,
          finalQC,
          variantCallFiles,
          reportFile,
          deliveryDir)
      }

    }
  }
}
