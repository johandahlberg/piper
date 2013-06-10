package molmed.qscripts

import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.gatk.phonehome.GATKRunReport
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.commandline.Hidden
import java.io.IOException
import org.broadinstitute.sting.commandline.ArgumentException

/**
 * TODO
 * - Clean up the argument list
 */

class VariantCalling extends QScript {
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

  @Input(doc = "Location of resource files such as dbSnp, hapmap, etc.", fullName = "resources", shortName = "res", required = true)
  var resources: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Argument(doc = "the project name determines the final output (vcf file) base name. Example NA12878 yields NA12878.vcf", fullName = "project", shortName = "p", required = false)
  var projectName: String = "project"

  @Argument(doc = "If the project is a low pass project", fullName = "lowpass", shortName = "lp", required = false)
  var isLowpass: Boolean = false

  @Argument(doc = "If the project is a exome sequencing project", fullName = "isExome", shortName = "ie", required = false)
  var isExome: Boolean = false

  @Input(doc = "an intervals file to be used by GATK - output bams at intervals only", fullName = "gatk_interval_file", shortName = "intervals", required = false)
  var intervals: File = _

  @Argument(doc = "Run the analysis for each bam file seperatly. By default all samples will be analyzed together", fullName = "analyze_separatly", shortName = "analyzeSeparatly", required = false)
  var runSeparatly = false

  @Argument(shortName = "outputDir", doc = "output directory", required = false)
  var outputDir: String = ""

  @Argument(shortName = "skipCalling", doc = "skip the calling part of the pipeline and only run VQSR on preset, gold standard VCF files", required = false)
  var skipCalling: Boolean = false

  @Argument(shortName = "runGoldStandard", doc = "run the pipeline with the goldstandard VCF files for comparison", required = false)
  var runGoldStandard: Boolean = false

  @Argument(shortName = "noBAQ", doc = "turns off BAQ calculation", required = false)
  var noBAQ: Boolean = false

  @Argument(shortName = "noIndels", doc = "do not call indels with the Unified Genotyper", required = false)
  var noIndels: Boolean = false

  @Argument(shortName = "noRecal", doc = "Skip recalibration", required = false)
  var noRecal: Boolean = false

  @Argument(shortName = "mbq", doc = "The minimum Phred-Scaled quality score threshold to be considered a good base.", required = false)
  var minimumBaseQuality: Int = -1

  @Argument(shortName = "deletions", doc = "Maximum deletion fraction allowed at a site to call a genotype.", required = false)
  var deletions: Double = -1

  @Argument(shortName = "sample", doc = "Samples to include in Variant Eval", required = false)
  var samples: List[String] = Nil

  @Hidden
  @Argument(doc = "How many ways to scatter/gather", fullName = "scatter_gather", shortName = "sg", required = false)
  var nContigs: Int = -1

  @Argument(doc = "Number of threads to use in thread enabled walkers. Default: 1", fullName = "nbr_of_threads", shortName = "nt", required = false)
  var nbrOfThreads: Int = 1

  @Argument(doc = "Downsample fraction. [0.0 - 1.0]", fullName = "downsample_to_fraction", shortName = "dtf", required = false)
  var downsampleFraction: Double = -1

  @Argument(doc = "Test mode", fullName = "test_mode", shortName = "test", required = false)
  var testMode: Boolean = false

  /**
   * Help class handling each variant calling target. Storing input files, creating output filenames etc.
   */
  class Target(
    val baseName: String,
    val reference: File,
    val dbsnpFile: String,
    val hapmapFile: String,
    val bamList: File,
    val goldStandard_VCF: File,
    val intervals: String,
    val isLowpass: Boolean,
    val isExome: Boolean,
    val nSamples: Int) {

    val name = qscript.outputDir.getAbsolutePath() + "/" + baseName
    val clusterFile = new File(name + ".clusters")
    val rawSnpVCF = new File(name + ".raw.snv.vcf")
    val rawIndelVCF = new File(name + ".raw.indel.vcf")
    val filteredIndelVCF = new File(name + ".filtered.indel.vcf")
    val recalibratedSnpVCF = new File(name + ".snp.recalibrated.snv.vcf")
    val recalibratedIndelVCF = new File(name + ".indel.recalibrated.vcf")
    val tranchesSnpFile = new File(name + ".snp.tranches")
    val tranchesIndelFile = new File(name + ".indel.tranches")
    val vqsrSnpRscript = name + ".snp.vqsr.r"
    val vqsrIndelRscript = name + ".indel.vqsr.r"
    val recalSnpFile = new File(name + ".snp.tranches.recal")
    val recalIndelFile = new File(name + ".indel.tranches.recal")
    val goldStandardRecalibratedVCF = new File(name + "goldStandard.recalibrated.vcf")
    val goldStandardTranchesFile = new File(name + "goldStandard.tranches")
    val goldStandardRecalFile = new File(name + "goldStandard.tranches.recal")
    val evalFile = new File(name + ".snp.eval")
    val evalIndelFile = new File(name + ".indel.eval")
    val goldStandardName = qscript.outputDir + "goldStandard/" + baseName
    val goldStandardClusterFile = new File(goldStandardName + ".clusters")
  }

  object Resources {

    logger.debug("Determining paths to resource files...")

    //TODO When xml setup is implemented, get the path to the resource files from there.
    def allFilesInResourceFiles: Array[File] = {
      try {
        if (resources.exists())
          resources.getAbsolutePath().listFiles()
        else
          throw new ArgumentException("Could not locate GATK bundle at: " + resources.getAbsolutePath())
      } catch {
        case e: ArgumentException => if (testMode) Array[File]() else throw e
      }
    }

    // For each resource get the matching file
    val dbsnp = getResourceFile(""".*dbsnp_137\.\w+\.vcf""")
    val hapmap = getResourceFile(""".*hapmap_3.3\.\w+\.vcf""")
    val omni = getResourceFile(""".*1000G_omni2.5\.\w+\.vcf""")
    val mills = getResourceFile(""".*Mills_and_1000G_gold_standard.indels\.\w+\.vcf""")

    logger.debug("Mapped dbsnp to: " + dbsnp)
    logger.debug("Mapped hapmap to: " + hapmap)
    logger.debug("Mapped omni to: " + omni)
    logger.debug("Mapped mills to: " + mills)

    def getResourceFile(regexp: String): File = {
      val resourceFile: Array[File] = allFilesInResourceFiles.filter(file => file.getName().matches(regexp))

      try {
        if (resourceFile.length == 1)
          resourceFile(0)
        else if (resourceFile.length > 1)
          throw new IOException("Found more than one file matching regular expression: " + regexp + " found files: " + resourceFile.mkString(", "))
        else
          throw new IOException("Found no file matching regular expression: " + regexp)
      } catch {
        case e: IOException => if (testMode) new File("") else throw e
      }

    }
  }

  //    val lowPass: Boolean = true
  //    val exome: Boolean = true
  //    val indels: Boolean = true

  val queueLogDir = ".qlog/"

  def script = {

    val bams = QScriptUtils.createSeqFromFile(input)

    // By default scatter over the contigs
    if (nContigs < 0)
      nContigs = QScriptUtils.getNumberOfContigs(bams(0))

    val targets = if (!runSeparatly)
      Seq(new Target(projectName, reference, Resources.dbsnp, Resources.hapmap, input, Resources.mills, intervals, isLowpass, isExome, bams.size))
    else {
      bams.map(bam => new Target(bam.getName(), reference, Resources.dbsnp, Resources.hapmap, bam, Resources.mills, intervals, isLowpass, isExome, 1))
    }

    for (target <- targets) {
      if (!skipCalling) {
        if (!noIndels) {
          // Indel calling, recalibration and evaulation
          add(new indelCall(target))
          if (!noRecal) {
            add(new indelRecal(target))
            add(new indelCut(target))
            add(new indelEvaluation(target))
          }
        }
        // SNP calling, recalibration and evaluation
        add(new snpCall(target))
        if (!noRecal) {
          add(new snpRecal(target))
          add(new snpCut(target))
          add(new snpEvaluation(target))
        }
      }
    }

  }

  trait UNIVERSAL_GATK_ARGS extends CommandLineGATK {
    logging_level = "DEBUG"
    this.memoryLimit = 24

    //TODO Add this when migrating to xml setup for all the scripts
    //this.jobNativeArgs +:= "-p node -A " + projId
  }

  def bai(bam: File) = new File(bam + ".bai")

  // 1.) Unified Genotyper Base
  class GenotyperBase(t: Target) extends UnifiedGenotyper with UNIVERSAL_GATK_ARGS {

    if (downsampleFraction != -1)
      this.downsample_to_fraction = downsampleFraction
    else
      this.dcov = if (t.isLowpass) { 50 } else { 250 }

    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
    this.scatterCount = nContigs
    this.nt = nbrOfThreads
    this.stand_call_conf = if (t.isLowpass) { 4.0 } else { 30.0 }
    this.stand_emit_conf = if (t.isLowpass) { 4.0 } else { 30.0 }
    this.input_file :+= t.bamList
    this.D = new File(t.dbsnpFile)
  }

  // 1a.) Call SNPs with UG
  class snpCall(t: Target) extends GenotyperBase(t) {
    if (minimumBaseQuality >= 0)
      this.min_base_quality_score = minimumBaseQuality
    if (qscript.deletions >= 0)
      this.max_deletion_fraction = qscript.deletions

    this.out = t.rawSnpVCF
    this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.SNP
    this.baq = if (noBAQ || t.isExome) { org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF } else { org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY }
    this.analysisName = t.name + "_UGs"
    this.jobName = queueLogDir + t.name + ".snpcall"
  }

  // 1b.) Call Indels with UG
  class indelCall(t: Target) extends GenotyperBase(t) {
    this.out = t.rawIndelVCF
    this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.INDEL
    this.baq = org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF
    this.analysisName = t.name + "_UGi"
    this.jobName = queueLogDir + t.name + ".indelcall"
  }

  // 2.) Hard Filtering for indels
  class indelFilter(t: Target) extends VariantFiltration with UNIVERSAL_GATK_ARGS {
    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
    this.scatterCount = nContigs
    this.V = t.rawIndelVCF
    this.out = t.filteredIndelVCF
    this.filterName ++= List("IndelQD", "IndelReadPosRankSum", "IndelFS")
    this.filterExpression ++= List("QD < 2.0", "ReadPosRankSum < -20.0", "FS > 200.0")

    if (t.nSamples >= 10) {
      this.filterName ++= List("IndelInbreedingCoeff")
      this.filterExpression ++= List("InbreedingCoeff < -0.8")
    }

    this.analysisName = t.name + "_VF"
    this.jobName = queueLogDir + t.name + ".indelfilter"
  }

  class VQSRBase(t: Target) extends VariantRecalibrator with UNIVERSAL_GATK_ARGS {
    this.nt = nbrOfThreads
    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
    this.allPoly = true
    this.tranche ++= List("100.0", "99.9", "99.5", "99.3", "99.0", "98.9", "98.8", "98.5", "98.4", "98.3", "98.2", "98.1", "98.0", "97.9", "97.8", "97.5", "97.0", "95.0", "90.0")
  }

  class snpRecal(t: Target) extends VQSRBase(t) with UNIVERSAL_GATK_ARGS {

    this.input :+= t.rawSnpVCF

    // Whole Genome sequencing
    this.resource :+= new TaggedFile(Resources.hapmap, "known=false,training=true,truth=true,prior=15.0")
    this.resource :+= new TaggedFile(Resources.omni, "known=false,training=true,truth=true,prior=12.0")
    this.resource :+= new TaggedFile(Resources.dbsnp, "known=true,training=false,truth=false,prior=6.0")

    //  From best practice: -an QD -an MQRankSum -an ReadPosRankSum -an FS -an DP
    this.use_annotation ++= List("QD", "HaplotypeScore", "MQRankSum", "ReadPosRankSum", "MQ", "FS", "DP")
    if (t.nSamples >= 10)
      this.use_annotation ++= List("InbreedingCoeff") // InbreedingCoeff is a population-wide statistic that requires at least 10 samples to calculate

    if (!t.isExome)
      this.use_annotation ++= List("DP")
    else { // exome specific parameters 

      this.mG = 6

      this.resource :+= new TaggedFile(Resources.hapmap, "known=false,training=true,truth=true,prior=15.0")
      this.resource :+= new TaggedFile(Resources.omni, "known=false,training=true,truth=false,prior=12.0")
      this.resource :+= new TaggedFile(Resources.dbsnp, "known=true,training=false,truth=false,prior=6.0")

      if (t.nSamples <= 30) { // very few exome samples means very few variants
        this.mG = 4
        this.percentBad = 0.04
      }
    }

    this.tranches_file = t.tranchesSnpFile
    this.recal_file = t.recalSnpFile

    this.rscript_file = t.vqsrSnpRscript
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    this.analysisName = t.name + "_VQSRs"
    this.jobName = queueLogDir + t.name + ".snprecal"
  }

  class indelRecal(t: Target) extends VQSRBase(t) with UNIVERSAL_GATK_ARGS {

    // Note that for indel recalication the same settings are used both for WGS and Exome Seq.

    this.input :+= t.rawIndelVCF
    this.resource :+= new TaggedFile(Resources.mills, "known=true,training=true,truth=true,prior=12.0")
    
    // From best practice: -an DP -an FS -an ReadPosRankSum -an MQRankSum
    this.use_annotation ++= List("QD", "ReadPosRankSum", "FS", "DP", "MQRankSum")

    this.mG = 4
    this.std = 10
    this.percentBad = 0.12

    if (t.nSamples >= 10)
      this.use_annotation ++= List("InbreedingCoeff") // InbreedingCoeff is a population-wide statistic that requires at least 10 samples to calculate

    this.tranches_file = t.tranchesIndelFile
    this.recal_file = t.recalIndelFile
    this.rscript_file = t.vqsrIndelRscript
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.INDEL
    this.analysisName = t.name + "_VQSRi"
    this.jobName = queueLogDir + t.name + ".indelrecal"
  }

  // 4.) Apply the recalibration table to the appropriate tranches
  class applyVQSRBase(t: Target) extends ApplyRecalibration with UNIVERSAL_GATK_ARGS {
    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
  }

  class snpCut(t: Target) extends applyVQSRBase(t) {

    this.input :+= t.rawSnpVCF
    this.tranches_file = t.tranchesSnpFile
    this.recal_file = t.recalSnpFile

    // By default this is 99.0
    // this.ts_filter_level = t.snpTrancheTarget
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    this.out = t.recalibratedSnpVCF
    this.analysisName = t.name + "_AVQSRs"
    this.jobName = queueLogDir + t.name + ".snpcut"
  }

  class indelCut(t: Target) extends applyVQSRBase(t) {
    this.input :+= t.rawIndelVCF
    this.tranches_file = t.tranchesIndelFile
    this.recal_file = t.recalIndelFile

    // By default this is 99.0
    //this.ts_filter_level = t.indelTranchTarget
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.INDEL
    this.out = t.recalibratedIndelVCF
    this.analysisName = t.name + "_AVQSRi"
    this.jobName = queueLogDir + t.name + ".indelcut"
  }

  // 5.) Variant Evaluation Base(OPTIONAL)
  class EvalBase(t: Target) extends VariantEval with UNIVERSAL_GATK_ARGS {
    this.comp :+= new TaggedFile(t.hapmapFile, "hapmap")
    this.D = new File(t.dbsnpFile)
    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
    this.sample = samples
  }

  // 5a.) SNP Evaluation (OPTIONAL) based on the cut vcf
  class snpEvaluation(t: Target) extends EvalBase(t) {
    // TODO Setup resonable comparisson file
    //if (t.reference == b37 || t.reference == hg19) this.comp :+= new TaggedFile( omni_b37, "omni" )
    this.eval :+= t.recalibratedSnpVCF
    this.out = t.evalFile
    this.analysisName = t.name + "_VEs"
    this.jobName = queueLogDir + t.name + ".snpeval"
  }

  // 5b.) Indel Evaluation (OPTIONAL)
  class indelEvaluation(t: Target) extends EvalBase(t) {
    this.eval :+= t.recalibratedIndelVCF

    // TODO Setup resonable comparisson file
    //this.comp :+= new TaggedFile(indelGoldStandardCallset, "indelGS" )
    this.noEV = true
    //TODO Check, if no eval modules are assigned, the standard ones are used.
    //this.evalModule = List("CompOverlap", "CountVariants", "TiTvVariantEvaluator", "ValidationReport", "IndelStatistics")
    this.out = t.evalIndelFile
    this.analysisName = t.name + "_VEi"
    this.jobName = queueLogDir + queueLogDir + t.name + ".indeleval"
  }

}
