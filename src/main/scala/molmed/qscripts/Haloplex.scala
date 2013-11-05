package molmed.qscripts

import java.io.FileNotFoundException
import java.io.PrintWriter
import scala.collection.JavaConversions._
import scala.io.Source
import org.broadinstitute.sting.commandline.Hidden
import org.broadinstitute.sting.gatk.downsampling.DownsampleType
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk.BamGatherFunction
import org.broadinstitute.sting.queue.extensions.gatk.BaseRecalibrator
import org.broadinstitute.sting.queue.extensions.gatk.ClipReads
import org.broadinstitute.sting.queue.extensions.gatk.CommandLineGATK
import org.broadinstitute.sting.queue.extensions.gatk.IndelRealigner
import org.broadinstitute.sting.queue.extensions.gatk.RealignerTargetCreator
import org.broadinstitute.sting.queue.extensions.gatk.UnifiedGenotyper
import org.broadinstitute.sting.queue.extensions.gatk.VariantFiltration
import org.broadinstitute.sting.queue.extensions.gatk.VcfGatherFunction
import org.broadinstitute.sting.queue.extensions.picard.MergeSamFiles
import org.broadinstitute.sting.queue.extensions.picard.SortSam
import org.broadinstitute.sting.queue.function.ListWriterFunction
import molmed.queue.extensions.picard.CollectTargetedPcrMetrics
import molmed.queue.setup.ReadGroupInformation
import molmed.queue.setup.ReadPairContainer
import molmed.queue.setup.Sample
import molmed.queue.setup.SampleAPI
import molmed.queue.setup.SetupXMLReader
import molmed.queue.setup.SetupXMLReaderAPI
import molmed.utils.Resources
import molmed.utils.GeneralUtils._
import net.sf.samtools.SAMFileHeader
import net.sf.samtools.SAMFileHeader.SortOrder
import net.sf.samtools.SAMFileReader
import net.sf.samtools.SAMTextHeaderCodec
import molmed.utils.ReadGroupUtils._
import molmed.utils.Uppmaxable
import molmed.utils.BwaAlignmentUtils
import molmed.utils.GeneralUtils

class Haloplex extends QScript with Uppmaxable {

  qscript =>

  /**
   * Arguments
   */

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "input pipeline setup xml", fullName = "input", shortName = "i", required = true)
  var input: File = _

  @Input(doc = "Location of resource files such as dbSnp, hapmap, etc.", fullName = "resources", shortName = "res", required = true)
  var resourcesPath: File = _

  @Input(doc = "bed files with haloplex intervals to be analyzed. (Covered from design package)", fullName = "gatk_interval_file", shortName = "intervals", required = true)
  var intervals: File = _

  @Input(doc = "Haloplex amplicons file", fullName = "amplicons", shortName = "amp", required = true)
  var amplicons: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName = "path_to_bwa", shortName = "bwa", required = false)
  var bwaPath: File = _

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = "/usr/bin/samtools"

  @Input(doc = "The path to the binary of butadapt", fullName = "path_to_cutadapt", shortName = "cutadapt", required = false)
  var cutadaptPath: File = _

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "Test mode", fullName = "test_mode", shortName = "test", required = false)
  var testMode: Boolean = false

  @Argument(doc = "How many ways to scatter/gather", fullName = "scatter_gather", shortName = "sg", required = false)
  var nContigs: Int = 23

  @Argument(doc = "Number of threads to use in thread enabled walkers. Default: 8", fullName = "nbr_of_threads", shortName = "nt", required = false)
  var nbrOfThreads: Int = 8

  @Argument(doc = "Downsample BQSR to x coverage (can get stuck in high coverage regions).", fullName = "downsample_bsqr", shortName = "dbsqr", required = false)
  var downsampleBQSR: Int = -1

  @Hidden
  @Input(doc = "Path to the sync script", fullName = "path_to_sync", shortName = "sync", required = false)
  var pathToSyncScript: File = "resources/FixEmptyReads.pl"

  /**
   * Private variables
   */
  private var resources: Resources = null

  /**
   * Helper methods
   */

  def getOutputDir(): File = {
    if (outputDir.isEmpty()) "" else outputDir + "/"
  }

  def cutSamples(sampleMap: Map[String, Seq[SampleAPI]], generalUtils: GeneralUtils): Map[String, Seq[SampleAPI]] = {

    // Standard Illumina adaptors    
    val adaptor1 = "AGATCGGAAGAGCACACGTCTGAACTCCAGTCAC"
    val adaptor2 = "AGATCGGAAGAGCGTCGTGTAGGGAAAGAGTGTAGATCTCGGTGGTCGCCGTATCATT"

    val cutadaptOutputDir = getOutputDir() + "/cutadapt"
    cutadaptOutputDir.mkdirs()

    // Run cutadapt & sync    

    def cutAndSyncSamples(samples: Seq[SampleAPI]): Seq[SampleAPI] = {

      def addSamples(sample: SampleAPI): SampleAPI = {

        def constructTrimmedName(name: String): String = {
          if (name.matches("fastq.gz"))
            name.replace("fastq.gz", "trimmed.fastq.gz")
          else
            name.replace("fastq", "trimmed.fastq.gz")
        }

        val readpairContainer = sample.getFastqs

        val mate1SyncedFastq = new File(cutadaptOutputDir + "/" + sample.getReadGroupInformation.platformUnitId + "/" + constructTrimmedName(sample.getFastqs.mate1.getName()))
        add(generalUtils.cutadapt(readpairContainer.mate1, mate1SyncedFastq, adaptor1, cutadaptPath))

        val mate2SyncedFastq =
          if (readpairContainer.isMatePaired) {
            val mate2SyncedFastq = new File(cutadaptOutputDir + "/" + sample.getReadGroupInformation.platformUnitId + "/" + constructTrimmedName(sample.getFastqs.mate2.getName()))
            add(generalUtils.cutadapt(readpairContainer.mate2, mate2SyncedFastq, adaptor2, cutadaptPath))
            mate2SyncedFastq
          } else null

        val readGroupContainer = new ReadPairContainer(mate1SyncedFastq, mate2SyncedFastq, sample.getSampleName)
        new Sample(sample.getSampleName, sample.getReference, sample.getReadGroupInformation, readGroupContainer)
      }

      val cutAndSyncedSamples = for (sample <- samples) yield { addSamples(sample) }
      cutAndSyncedSamples

    }

    val cutSamples = for { (sampleName, samples) <- sampleMap }
      yield (sampleName, cutAndSyncSamples(samples))

    cutSamples
  }

  // Override the normal swapExt metod by adding the outputDir to the file path by default if it is defined.
  override def swapExt(file: File, oldExtension: String, newExtension: String) = {
    if (outputDir.isEmpty())
      new File(file.getName.stripSuffix(oldExtension) + newExtension)
    else
      swapExt(outputDir, file, oldExtension, newExtension);
  }

  /**
   * The actual script
   */

  def script() = {

    resources = new Resources(resourcesPath, testMode)
    val generalUtils = new GeneralUtils(projectName, projId, uppmaxQoSFlag)

    // Create output dirs
    val vcfOutputDir = new File(getOutputDir() + "/vcf_files")
    vcfOutputDir.mkdirs()
    val miscOutputDir = new File(getOutputDir() + "/misc")
    miscOutputDir.mkdirs()
    val bamOutputDir = new File(getOutputDir() + "/bam_files")
    bamOutputDir.mkdirs()

    def setupSamples(): Map[String, Seq[SampleAPI]] = {
      val setupReader: SetupXMLReaderAPI = new SetupXMLReader(input)
      projId = setupReader.getUppmaxProjectId()
      projectName = setupReader.getProjectName()
      uppmaxQoSFlag = setupReader.getUppmaxQoSFlag()
      setupReader.getSamples()
    }

    // Get and setup input files
    val samples: Map[String, Seq[SampleAPI]] = setupSamples()
    // Run cutadapt       
    val cutAndSyncedSamples = cutSamples(samples, generalUtils)
    val alignmentHelper = new BwaAlignmentUtils(this, bwaPath, nbrOfThreads, samtoolsPath, projectName, projId, uppmaxQoSFlag)

    // Align with bwa
    val cohortList =
      cutAndSyncedSamples.values.flatten.map(sample => alignmentHelper.align(sample, bamOutputDir, false)).toSeq

    // Make raw variation calls
    val preliminaryVariantCalls = new File(vcfOutputDir + "/" + projectName + ".pre.vcf")
    val reference = samples.values.flatten.toList(0).getReference
    add(genotype(cohortList.toSeq, reference, preliminaryVariantCalls, false))

    // Create realignment targets
    val targets = new File(miscOutputDir + "/" + projectName + ".targets.intervals")
    add(target(preliminaryVariantCalls, targets, reference))

    // Do indel realignment
    val postCleaningBamList =
      for (bam <- cohortList) yield {

        // Indel realignment
        val indelRealignedBam = swapExt(bamOutputDir, bam, ".bam", ".clean.bam")
        add(clean(Seq(bam), targets, indelRealignedBam, reference))
        indelRealignedBam
      }

    // BQSR
    val covariates = new File(miscOutputDir + "/bqsr.grp")
    add(cov(postCleaningBamList.toSeq, covariates, reference))

    // Clip reads and apply BQSR
    val clippedAndRecalibratedBams =
      for (bam <- postCleaningBamList) yield {
        val clippedBam = swapExt(bamOutputDir, bam, ".bam", ".clipped.recal.bam")
        add(clip(bam, clippedBam, covariates, reference))
        clippedBam
      }

    // Convert intervals and amplicons from bed files to
    // picard metric files.
    val intervalsAsPicardIntervalFile = new File(swapExt(miscOutputDir, qscript.intervals, ".bed", ".intervals"))
    val ampliconsAsPicardIntervalFile = new File(swapExt(miscOutputDir, qscript.amplicons, ".bed", ".intervals"))

    add(convertCoveredToIntervals(qscript.intervals, intervalsAsPicardIntervalFile, clippedAndRecalibratedBams.toList(0)))
    add(convertAmpliconsToIntervals(qscript.amplicons, ampliconsAsPicardIntervalFile, clippedAndRecalibratedBams.toList(0)))

    // Collect targetedPCRMetrics
    for (bam <- clippedAndRecalibratedBams) {
      val generalStatisticsOutputFile = swapExt(bamOutputDir, bam, ".bam", ".statistics")
      val perAmpliconStatisticsOutputFile = swapExt(bamOutputDir, bam, ".bam", ".amplicon.statistics")
      add(collectTargetedPCRMetrics(bam, generalStatisticsOutputFile, perAmpliconStatisticsOutputFile,
        ampliconsAsPicardIntervalFile, intervalsAsPicardIntervalFile, reference))
    }

    // Make variant calls
    val afterCleanupVariants = swapExt(vcfOutputDir, preliminaryVariantCalls, ".pre.vcf", ".vcf")
    add(genotype(clippedAndRecalibratedBams.toSeq, reference, afterCleanupVariants, true))

    // Filter variant calls
    val filteredCallSet = swapExt(vcfOutputDir, afterCleanupVariants, ".vcf", ".filtered.vcf")
    add(filterVariations(afterCleanupVariants, filteredCallSet, reference))

  }

  /**
   * Case class wappers for external programs
   */

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {

    val qosFlag = if (!uppmaxQoSFlag.isEmpty) " --qos=" + uppmaxQoSFlag.get else ""
    this.jobNativeArgs +:= "-p node -A " + projId + " " + qosFlag
    this.memoryLimit = 24
    this.isIntermediate = false
  }

  // General arguments to GATK walkers
  trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {} 

  def intervalFormatString(contig: String, start: String, end: String, strand: String, intervalName: String): String =
    "%s\t%s\t%s\t%s\t%s".format(contig, start, end, strand, intervalName)

  def formatFromCovered(split: Array[String]): String = {
    intervalFormatString(split(0), (split(1).toInt + 1).toString, split(2), "+", split(3))
  }

  def formatFromAmplicons(split: Array[String]): String = {
    intervalFormatString(split(0), (split(1).toInt + 1).toString, split(2), split(5), split(3))
  }

  def writeIntervals(bed: File, intervalFile: File, bam: File, formatFrom: Array[String] => String): Unit = {

    def getSamHeader(bam: File): SAMFileHeader = {
      val samReader = new SAMFileReader(bam)
      samReader.getFileHeader
    }

    val header = getSamHeader(bam)
    header.setProgramRecords(List())
    header.setReadGroups(List())

    val writer = new PrintWriter(intervalFile)

    val codec = new SAMTextHeaderCodec();
    codec.encode(writer, header)

    for (row <- Source.fromFile(bed).getLines.drop(2)) {
      val split = row.split("\t")
      val intervalEntry = formatFrom(split)
      writer.println(intervalEntry)
    }
    writer.close()
  }

  case class convertCoveredToIntervals(@Input bed: File, @Output intervalFile: File, @Input bam: File) extends InProcessFunction {
    def run(): Unit = {
      writeIntervals(bed, intervalFile, bam, formatFromCovered)
    }
  }

  case class convertAmpliconsToIntervals(@Input bed: File, @Output intervalFile: File, @Input bam: File) extends InProcessFunction {
    def run(): Unit = {
      writeIntervals(bed, intervalFile, bam, formatFromAmplicons)
    }
  }

  case class genotype(@Input bam: Seq[File], reference: File, @Output @Gather(classOf[VcfGatherFunction]) vcf: File, isSecondPass: Boolean) extends UnifiedGenotyper with CommandLineGATKArgs {

    if (qscript.testMode)
      this.no_cmdline_in_header = true

    this.isIntermediate = false

    this.input_file = bam
    this.out = vcf

    this.dbsnp = resources.dbsnp
    this.reference_sequence = reference
    this.intervals = Seq(qscript.intervals)
    this.scatterCount = nContigs
    this.nt = nbrOfThreads
    this.stand_call_conf = 30.0
    this.stand_emit_conf = 10.0

    // Depending on if this is used to call preliminary or final variations different
    // parameters should be used.
    if (isSecondPass) {
      this.dt = DownsampleType.NONE
      this.annotation = Seq("AlleleBalance")
      this.filterMBQ = true
    } else {
      this.downsample_to_coverage = 200
    }

    this.output_mode = org.broadinstitute.sting.gatk.walkers.genotyper.UnifiedGenotyperEngine.OUTPUT_MODE.EMIT_VARIANTS_ONLY
    this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.BOTH

    this.analysisName = projectName + "_genotype"
    this.jobName = projectName + "_genotype"
  }

  case class target(@Input candidateIndels: File, outIntervals: File, reference: File) extends RealignerTargetCreator with CommandLineGATKArgs {

    this.reference_sequence = reference
    this.num_threads = nbrOfThreads
    this.intervals = Seq(qscript.intervals)
    this.out = outIntervals
    this.mismatchFraction = 0.0
    this.known :+= resources.mills
    this.known :+= resources.phase1
    this.known :+= candidateIndels
    this.scatterCount = nContigs
    this.analysisName = projectName + "_target"
    this.jobName = projectName + "_target"
  }

  case class clean(inBams: Seq[File], tIntervals: File, outBam: File, reference: File) extends IndelRealigner with CommandLineGATKArgs {

    this.isIntermediate = true
    this.reference_sequence = reference
    this.input_file = inBams
    this.targetIntervals = tIntervals
    this.out = outBam
    this.known :+= resources.dbsnp
    this.known :+= resources.mills
    this.known :+= resources.phase1
    this.consensusDeterminationModel = org.broadinstitute.sting.gatk.walkers.indels.IndelRealigner.ConsensusDeterminationModel.KNOWNS_ONLY
    this.compress = 0
    this.scatterCount = nContigs
    this.analysisName = projectName + "_clean"
    this.jobName = projectName + "_clean"
  }

  case class cov(inBam: Seq[File], outRecalFile: File, reference: File) extends BaseRecalibrator with CommandLineGATKArgs {

    // Ask for a fat node
    this.jobNativeArgs :+= " -C fat "

    this.reference_sequence = reference
    this.isIntermediate = false

    this.num_cpu_threads_per_data_thread = nbrOfThreads

    if (qscript.downsampleBQSR != -1)
      this.downsample_to_coverage = qscript.downsampleBQSR
    this.knownSites :+= resources.dbsnp
    this.covariate ++= Seq("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "ContextCovariate")
    this.input_file = inBam
    this.disable_indel_quals = false
    this.out = outRecalFile
    this.intervals = Seq(qscript.intervals)

    this.scatterCount = nContigs
    this.analysisName = projectName + "_cov"
    this.jobName = projectName + "_cov"
  }

  case class clip(@Input inBam: File, @Output @Gather(classOf[BamGatherFunction]) outBam: File, covariates: File, reference: File) extends ClipReads with CommandLineGATKArgs {
    this.isIntermediate = false
    this.reference_sequence = reference
    this.input_file = Seq(inBam)
    this.out = outBam
    this.cyclesToTrim = "1-5"
    this.scatterCount = nContigs
    this.clipRepresentation = org.broadinstitute.sting.utils.clipping.ClippingRepresentation.WRITE_NS
    this.BQSR = covariates

    this.analysisName = projectName + "_clean"
    this.jobName = projectName + "_clean"
  }

  case class filterVariations(@Input inVcf: File, @Output outVcf: File, reference: File) extends VariantFiltration with CommandLineGATKArgs {

    if (qscript.testMode)
      this.no_cmdline_in_header = true

    this.reference_sequence = reference
    this.variant = inVcf
    this.out = outVcf

    this.clusterWindowSize = 10
    this.clusterSize = 3
    this.filterExpression = Seq("MQ0 >= 4 && (( MQ0 / (1.0 * DP )) > 0.1)",
      "DP < 10",
      "QUAL < 30.0",
      "QUAL > 30.0 && QUAL < 50.0",
      "QD < 1.5")

    this.filterName = Seq("HARD_TO_VALIDATE", "LowCoverage", "VeryLowQual", "LowQual", "LowQD")

    this.analysisName = projectName + "_filterVariants"
    this.jobName = projectName + "_filterVariants"

  }

  case class collectTargetedPCRMetrics(bam: File, generalStatisticsOutput: File, perTargetStat: File, ampliconIntervalFile: File, targetIntevalFile: File, ref: File) extends CollectTargetedPcrMetrics with ExternalCommonArgs {

    this.isIntermediate = false

    this.input = Seq(bam)
    this.output = generalStatisticsOutput
    this.perTargetOutputFile = perTargetStat
    this.amplicons = ampliconIntervalFile
    this.targets = targetIntevalFile
    this.reference = ref

    this.analysisName = projectName + "_collectPCRMetrics"
    this.jobName = projectName + "_collectPCRMetrics"

  }

}