package molmed.qscripts

import java.io.FileNotFoundException
import scala.collection.JavaConversions._
import org.broadinstitute.sting.commandline.Hidden
import org.broadinstitute.sting.gatk.walkers.indels.IndelRealigner.ConsensusDeterminationModel
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.utils.baq.BAQ.CalculationMode
import net.sf.picard.reference.IndexedFastaSequenceFile
import net.sf.samtools.SAMFileHeader.SortOrder
import net.sf.samtools.SAMFileReader

class RNAVariantCalling extends QScript {
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

  @Input(doc = "dbsnp ROD to use (must be in VCF format)", fullName = "dbsnp", shortName = "D", required = true)
  var dbSNP: File = _

  @Argument(doc = "", fullName = "vep_path", shortName = "veppath", required = true)
  var vepPath: String = ""

  @Argument(doc = "", fullName = "mapability_50mer", shortName = "mapability50mer", required = true)
  var mapability50mer: String = ""

  @Argument(doc = "", fullName = "mapability_100mer", shortName = "mapability100mer", required = true)
  var mapability100mer: String = ""

  @Argument(doc = "", fullName = "COSMIC_1", shortName = "C_1", required = true)
  var COSMIC_1: String = ""

  @Argument(doc = "", fullName = "COSMIC_2", shortName = "C_2", required = true)
  var COSMIC_2: String = ""

  @Argument(doc = "", fullName = "GENOMIC_SUPER_DUPS", shortName = "gsd", required = true)
  var GENOMIC_SUPER_DUPS: String = ""

  @Argument(doc = "", fullName = "SELFCHAIN", shortName = "sc", required = true)
  var SELFCHAIN: String = ""

  @Argument(doc = "", fullName = "ESP_ESP6500SI_V2", shortName = "eev2", required = true)
  var ESP_ESP6500SI_V2: String = ""

  @Argument(doc = "", fullName = "RNA_EDITING", shortName = "rnaedit", required = true)
  var RNA_EDITING: String = ""

  @Argument(doc = "", fullName = "REPEATMASKER", shortName = "rptmsk", required = true)
  var REPEATMASKER: String = ""

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "extra VCF files to use as reference indels for Indel Realignment", fullName = "extra_indels", shortName = "indels", required = false)
  var indels: Seq[File] = Seq()

  @Argument(doc = "the project name determines the final output (BAM file) base name. Example NA12878 yields NA12878.processed.bam", fullName = "project", shortName = "p", required = false)
  var projectName: String = "project"

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "the -L interval string to be used by GATK - output bams at interval only", fullName = "gatk_interval_string", shortName = "L", required = false)
  var intervalString: String = ""

  @Input(doc = "an intervals file to be used by GATK - output bams at intervals only", fullName = "gatk_interval_file", shortName = "intervals", required = false)
  var intervals: File = _

  @Argument(doc = "Perform validation on the BAM files", fullName = "validation", shortName = "vs", required = false)
  var validation: Boolean = false

  @Argument(doc = "Number of threads to use in thread enabled walkers. Default: 1", fullName = "nbr_of_threads", shortName = "nt", required = false)
  var nbrOfThreads: Int = 1

  @Argument(shortName = "mbq", doc = "The minimum Phred-Scaled quality score threshold to be considered a good base.", required = false)
  var minimumBaseQuality: Int = -1

  @Argument(shortName = "deletions", doc = "Maximum deletion fraction allowed at a site to call a genotype.", required = false)
  var deletions: Double = -1

  @Argument(doc = "Downsample fraction. [0.0 - 1.0]", fullName = "downsample_to_fraction", shortName = "dtf", required = false)
  var downsampleFraction: Double = -1

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

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {
    // final output list of processed bam files
    var cohortList: Seq[File] = Seq()

    // keep a record of the number of contigs in the first bam file in the list
    val acceptedHitsBams = QScriptUtils.createSeqFromFile(input)
    val bams = acceptedHitsBams.map(file => {

      val samReader = new SAMFileReader(file)
      val header = samReader.getFileHeader
      val readGroups = header.getReadGroups

      val sampleSet = readGroups.map(f => f.getSample()).toSet

      require(sampleSet.size == 1, "There were more than one sample in file: " + file +
        ". Please make sure there is only one sample per file before running RNAVariantCalling.")

      (file, sampleSet.toSeq(0))

    })

    // Scatter gatter to 23 or the number of contigs, depending on which is the smallest.
    if (nContigs < 0) {
      nContigs = math.min(QScriptUtils.getNumberOfContigs(bams(0)._1), 23)
    }

    // put each sample through the pipeline
    for ((file, name) <- bams) {

      // BAM files generated by the pipeline      
      val bam = if (outputDir.isEmpty())
        new File(qscript.projectName + "." + name + ".bam")
      else
        new File(outputDir + qscript.projectName + "." + name + ".bam")

      val dedupedBam = swapExt(bam, ".bam", ".dedup.bam")
      val recalBam = swapExt(bam, ".bam", ".dedup.recal.bam")

      // Accessory files
      val metricsFile = swapExt(bam, ".bam", ".metrics")
      val preRecalFile = swapExt(bam, ".bam", ".pre_recal.table")
      val postRecalFile = swapExt(bam, ".bam", ".post_recal.table")
      val preOutPath = swapExt(bam, ".bam", ".pre")
      val postOutPath = swapExt(bam, ".bam", ".post")
      val preValidateLog = swapExt(bam, ".bam", ".pre.validation")
      val postValidateLog = swapExt(bam, ".bam", ".post.validation")

      // Deduplicate
      add(dedup(file, dedupedBam, metricsFile))

      // Recalibrate
      add(cov(dedupedBam, preRecalFile),
        recal(dedupedBam, preRecalFile, recalBam),
        cov(recalBam, postRecalFile))

      cohortList :+= recalBam
    }

    val candidateSnps = new File(outputDir + "/" + projectName + ".candidate.snp.vcf")
    val candidateIndels = new File(outputDir + "/" + projectName + ".candidate.indel.vcf")

    // SNP and INDEL Calls
    add(snpCall(cohortList, candidateSnps))
    add(indelCall(cohortList, candidateIndels))

    val targets = new File(outputDir + "/" + projectName + ".targets")
    add(target(candidateIndels, targets))

    // Take regions based on indels called in previous step
    val postCleaningBamList =
      for (bam <- cohortList) yield {
        val indelRealignedBam = swapExt(bam, ".bam", ".clean.bam")
        add(clean(Seq(bam), targets, indelRealignedBam))
        indelRealignedBam
      }

    val afterCleanupSnps = swapExt(candidateSnps, ".candidate.snp.vcf", ".cleaned.snp.vcf")
    val afterCleanupIndels = swapExt(candidateIndels, ".candidate.indel.vcf", ".cleaned.indel.vcf")

    // Call snps/indels again (possibly only in previously identified regions)
    add(snpCall(postCleaningBamList, afterCleanupSnps))
    add(indelCall(postCleaningBamList, afterCleanupIndels, 1))

    // Variant effect predictor - get all variants which change a aa
    val finalSnps = swapExt(candidateSnps, ".cleaned.snp.vcf", ".final.snp.vcf")
    add(variantEffectPredictor(afterCleanupSnps, finalSnps))

    val finalIndels = swapExt(candidateIndels, ".cleaned.indel.vcf", ".final.indel.vcf")
    add(variantEffectPredictor(afterCleanupIndels, finalIndels))

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

  def bai(bam: File) = new File(bam + ".bai")

  // 1.) Unified Genotyper Base
  class GenotyperBase(bam: Seq[File]) extends UnifiedGenotyper with CommandLineGATKArgs {

    this.isIntermediate = false

    if (downsampleFraction != -1)
      this.downsample_to_fraction = downsampleFraction

    this.reference_sequence = reference
    this.intervals = intervals
    this.scatterCount = nContigs
    this.nt = nbrOfThreads
    this.stand_call_conf = 50.0
    this.stand_emit_conf = 10.0
    this.input_file = bam
    this.D = qscript.dbSNP
  }

  // Call SNPs with UG
  case class snpCall(bam: Seq[File], vcf: File) extends GenotyperBase(bam) {
    if (minimumBaseQuality >= 0)
      this.min_base_quality_score = minimumBaseQuality
    if (qscript.deletions >= 0)
      this.max_deletion_fraction = qscript.deletions

    this.out = vcf
    this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.SNP
    this.baq = org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY
    this.analysisName = "UG_snp"
    this.jobName = "UG_snp"
  }

  // Call Indels with UG
  case class indelCall(bam: Seq[File], vcf: File, scatterGather: Int = nContigs) extends GenotyperBase(bam) {
    //@TODO See if this hacky solution can be removed.
    this.scatterCount = scatterGather
    this.out = vcf
    this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.INDEL
    this.baq = org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF
    this.analysisName = "UG_Indel"
    this.jobName = "UG_Indel"
  }

  case class target(@Input candidateIndels: File, outIntervals: File) extends RealignerTargetCreator with CommandLineGATKArgs {

    this.num_threads = nbrOfThreads
    this.out = outIntervals
    this.mismatchFraction = 0.0
    this.known :+= qscript.dbSNP
    this.known :+= candidateIndels
    if (indels != null)
      this.known ++= qscript.indels
    this.scatterCount = nContigs
    this.analysisName = queueLogDir + outIntervals + ".target"
    this.jobName = queueLogDir + outIntervals + ".target"
  }

  case class clean(inBams: Seq[File], tIntervals: File, outBam: File) extends IndelRealigner with CommandLineGATKArgs {
    this.input_file = inBams
    this.targetIntervals = tIntervals
    this.out = outBam
    this.known :+= qscript.dbSNP
    if (qscript.indels != null)
      this.known ++= qscript.indels
    this.consensusDeterminationModel = ConsensusDeterminationModel.KNOWNS_ONLY
    this.compress = 0
    this.noPGTag = qscript.testMode;
    this.scatterCount = nContigs
    this.analysisName = queueLogDir + outBam + ".clean"
    this.jobName = queueLogDir + outBam + ".clean"
  }

  case class cov(inBam: File, outRecalFile: File) extends BaseRecalibrator with CommandLineGATKArgs {

    this.isIntermediate = false

    this.num_cpu_threads_per_data_thread = nbrOfThreads

    this.knownSites :+= qscript.dbSNP
    this.covariate ++= Seq("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "ContextCovariate")
    this.input_file :+= inBam
    this.disable_indel_quals = false
    this.out = outRecalFile
    if (!defaultPlatform.isEmpty) this.default_platform = defaultPlatform
    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

    this.scatterCount = nContigs
    this.analysisName = queueLogDir + outRecalFile + ".covariates"
    this.jobName = queueLogDir + outRecalFile + ".covariates"
  }

  case class recal(inBam: File, inRecalFile: File, outBam: File) extends PrintReads with CommandLineGATKArgs {

    this.input_file :+= inBam

    // TODO According to this thread: 
    // http://gatkforums.broadinstitute.org/discussion/2267/baq-tag-error#latest
    // There is a bug in the GATK which means that the baq calculations should not
    // be done in this step, but rather in the unified genotyper.
    this.BQSR = inRecalFile
    //this.baq = CalculationMode.CALCULATE_AS_NECESSARY
    this.out = outBam
    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals
    this.scatterCount = nContigs
    this.num_cpu_threads_per_data_thread = nbrOfThreads
    this.isIntermediate = false
    this.analysisName = queueLogDir + outBam + ".recalibration"
    this.jobName = queueLogDir + outBam + ".recalibration"
  }

  /**
   * **************************************************************************
   * Classes (non-GATK programs)
   * **************************************************************************
   */

  case class dedup(inBam: File, outBam: File, metricsFile: File) extends MarkDuplicates with ExternalCommonArgs {

    this.input :+= inBam
    this.output = outBam
    this.metrics = metricsFile
    this.memoryLimit = 16
    this.analysisName = queueLogDir + outBam + ".dedup"
    this.jobName = queueLogDir + outBam + ".dedup"
  }

  case class variantEffectPredictor(@Input inputVcf: File, @Output outputVcf: File) extends ExternalCommonArgs {

    this.isIntermediate = false
    def commandLine = "perl " + vepPath + " -i " + inputVcf + " -o " + outputVcf +
      " --coding_only " +
      " --sift b " +
      " --polyphen b " +
      " --vcf " +
      " --filter coding_change " +
      " --no_progress " +
      " -force " +
      " -custom " + mapability50mer + ",Mapability50,bed,overlap" +
      " -custom " + mapability100mer + ",Mapability100,bed,overlap" +
      " -custom " + COSMIC_1 + ",cosmic_wgs,vcf,overlap" +
      " -custom " + COSMIC_2 + ",cosmic,vcf,overlap" +
      " -custom " + GENOMIC_SUPER_DUPS + ",SuperDups,bed,overlap" +
      " -custom " + SELFCHAIN + ",selfChain,bed,overlap" +
      " -custom " + ESP_ESP6500SI_V2 + ",ExomeDB,bed,overlap" +
      " -custom " + RNA_EDITING + ",RNAEditing,bed,overlap" +
      " -custom " + REPEATMASKER + ",repeatMask,bed,overlap"
  }

  case class sortSam(inSam: File, outBam: File, sortOrderP: SortOrder) extends SortSam with ExternalCommonArgs {
    this.input :+= inSam
    this.output = outBam
    this.sortOrder = sortOrderP
    this.analysisName = queueLogDir + outBam + ".sortSam"
    this.jobName = queueLogDir + outBam + ".sortSam"
  }

  case class writeList(inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    this.analysisName = queueLogDir + outBamList + ".bamList"
    this.jobName = queueLogDir + outBamList + ".bamList"
  }
}
