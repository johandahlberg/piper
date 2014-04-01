package molmed.qscripts

import org.broadinstitute.sting.commandline.Hidden
import org.broadinstitute.sting.queue.QScript
import molmed.queue.setup.SampleAPI
import molmed.utils.AlignerOption
import molmed.utils.AlignmentQCUtils
import molmed.utils.BwaAlignmentUtils
import molmed.utils.BwaAln
import molmed.utils.BwaMem
import molmed.utils.GATKConfig
import molmed.utils.GATKDataProcessingUtils
import molmed.utils.GeneralUtils
import molmed.utils.MergeFilesUtils
import molmed.utils.UppmaxXMLConfiguration
import molmed.utils.VariantCallingConfig
import molmed.utils.VariantCallingUtils
import molmed.utils.GATKHaplotypeCaller
import molmed.utils.GATKUnifiedGenotyper
import molmed.utils.VariantCallerOption
import molmed.utils.VariantCallerOption

/**
 *
 * Run broads recommended pipeline for DNA variant calling:
 *
 *  Should work for both exomes and whole genomes.
 *
 */

class DNABestPracticeVariantCalling extends QScript with UppmaxXMLConfiguration {
  qscript =>

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "an intervals file to be used by GATK - output bams at intervals only", fullName = "gatk_interval_file", shortName = "intervals", required = false)
  var intervals: File = _

  @Input(doc = "dbsnp ROD to use (must be in VCF format)", fullName = "dbsnp", shortName = "D", required = false)
  var dbSNP: File = _

  @Input(doc = "extra VCF files to use as reference indels for Indel Realignment", fullName = "extra_indels", shortName = "indels", required = false)
  var indels: Seq[File] = Seq()

  @Input(doc = "HapMap file to use with variant recalibration.", fullName = "hapmap", shortName = "hm", required = false)
  var hapmap: File = _

  @Input(doc = "Omni file fo use with variant recalibration ", fullName = "omni", shortName = "om", required = false)
  var omni: File = _

  @Input(doc = "Mills indel file to use with variant recalibration", fullName = "mills", shortName = "mi", required = false)
  var mills: File = _

  @Argument(doc = "Cleaning model: KNOWNS_ONLY, USE_READS or USE_SW. (Default: USE_READS)", fullName = "clean_model", shortName = "cm", required = false)
  var cleaningModel: String = "USE_READS"

  @Input(doc = "The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName = "path_to_bwa", shortName = "bwa", required = false)
  var bwaPath: File = _

  @Argument(doc = "The type of bwa aligner to use. Options are BWA_MEM and BWA_ALN. (Default: BWA_MEM)", fullName = "bwa_aligner", shortName = "bwaa", required = false)
  var bwaAlignerType: String = "BWA_MEM"

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = "samtools"

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = "pipeline_output"

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

  @Argument(doc = "Only do the aligments - useful when there is more data to be delivered in a project", fullName = "onlyAlignments", shortName = "oa", required = false)
  var onlyAlignment: Boolean = false

  @Argument(doc = "Remove the raw merged alignment files.", fullName = "remove_raw_merged_alignments", shortName = "rrma", required = false)
  var removeMergedAlignments: Boolean = false

  @Argument(doc = "Indicate if the libraries was prepared using a PCR free library or not.", fullName = "pcr_free_libraries", shortName = "pcrfree", required = false)
  var pcrFreeLibrary: Boolean = false
    
  @Argument(doc = "Choose which variant caller to use. Options are: HaplotypeCaller, UnifiedGenotyper", fullName = "variant_caller", shortName = "vc", required = false)
  var variantCaller: String = "HaplotypeCaller"
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
      case "HaplotypeCaller" => Some(GATKHaplotypeCaller)
      case "UnifiedGenotyper" => Some(GATKUnifiedGenotyper)
      case s: String => throw new IllegalArgumentException("Did not recognize aligner option: " + s)
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
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {

    /**
     * Defining output dirs for the different parts of the run
     */

    val aligmentOutputDir: File = new File(outputDir + "/raw_alignments")
    val mergedAligmentOutputDir: File = new File(outputDir + "/merged_aligments")
    val aligmentQCOutputDir: File = new File(outputDir + "/alignment_qc")
    val processedAligmentsOutputDir: File = new File(outputDir + "/processed_alignments")
    val variantCallsOutputDir: File = new File(outputDir + "/variant_calls")

    /**
     * Setup of resources to use
     */

    val uppmaxConfig = loadUppmaxConfigFromXML(testMode = qscript.testMode)
    val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
    // NOTE: assumes all samples are to be aligned to the same reference.
    val reference = samples.head._2(0).getReference()

    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)

    val gatkOptions = {
      implicit def file2Option(file: File) = if (file == null) None else Some(file)
      new GATKConfig(reference, nbrOfThreads, scatterGatherCount, intervals, dbSNP, Some(indels), hapmap, omni, mills)
    }

    /**
     * Run alignments
     */
    val aligner: Option[AlignerOption] = decideAlignerType(bwaAlignerType)
    val alignmentUtils = new BwaAlignmentUtils(this, bwaPath, nbrOfThreads, samtoolsPath, projectName, uppmaxConfig)
    val sampleNamesAndalignedBamFiles = samples.values.flatten.map(sample =>
      (sample.getSampleName,
        alignmentUtils.align(sample, aligmentOutputDir, asIntermidate = !onlyAlignment, aligner)))
    val sampleNamesToBamMap = sampleNamesAndalignedBamFiles.groupBy(f => f._1).mapValues(f => f.map(x => x._2).toSeq)

    // Stop here is only aligments option is enabled.
    if (!onlyAlignment) {

      /**
       * Merge by sample
       */
      val mergeFilesUtils = new MergeFilesUtils(this, projectName, uppmaxConfig)
      val mergedBamFiles = mergeFilesUtils.mergeFilesBySampleName(sampleNamesToBamMap, mergedAligmentOutputDir, asIntermediate = removeMergedAlignments)

      /**
       * Get QC statistics
       */
      val qualityControlUtils = new AlignmentQCUtils(qscript, gatkOptions, projectName, uppmaxConfig)
      val qualityControlPassed = qualityControlUtils.aligmentQC(mergedBamFiles, aligmentQCOutputDir)

      /**
       * Data processing
       */

      // Only processed with samples where quality control has passed
      // @TODO Note that this has not been implemented yet.
      val samplesWhichHavePassedQC = qualityControlPassed.filter(p => p._2).map(_._1)
      val gatkDataProcessingUtils = new GATKDataProcessingUtils(this, gatkOptions, generalUtils, projectName, uppmaxConfig)
      val processedBamFiles = gatkDataProcessingUtils.dataProcessing(bams = samplesWhichHavePassedQC, processedAligmentsOutputDir, cleaningModel, skipDeduplication = false, testMode)

      /**
       * Variant calling
       */      
      val variantCallerToUse: Option[VariantCallerOption] = decideVariantCallerType(variantCaller)
      
      val variantCallingUtils = new VariantCallingUtils(gatkOptions, projectName, uppmaxConfig)
      val variantCallingConfig = new VariantCallingConfig(
        qscript = this,
        variantCaller = variantCallerToUse,
        processedBamFiles,
        variantCallsOutputDir,
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

  }
}
