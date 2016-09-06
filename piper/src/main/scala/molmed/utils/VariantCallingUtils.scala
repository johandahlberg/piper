package molmed.utils

import org.broadinstitute.gatk.queue.extensions.gatk.UnifiedGenotyper
import java.io.File
import org.broadinstitute.gatk.queue.extensions.gatk.VariantFiltration
import org.broadinstitute.gatk.queue.extensions.gatk.VariantRecalibrator
import org.broadinstitute.gatk.queue.extensions.gatk.TaggedFile
import org.broadinstitute.gatk.queue.extensions.gatk.ApplyRecalibration
import org.broadinstitute.gatk.queue.extensions.gatk.VariantEval
import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.gatk.HaplotypeCaller
import org.broadinstitute.gatk.queue.extensions.gatk.GenotypeGVCFs
import org.broadinstitute.gatk.queue.extensions.gatk.SelectVariants
import org.broadinstitute.gatk.queue.extensions.gatk.GenotypeConcordance
import org.broadinstitute.gatk.queue.function.CommandLineFunction

/**
 * Wrapping case classed and functions for doing variant calling using the GATK.
 *
 * Contains code both to use the Unified genotyper and the Haplotype caller.
 */
class VariantCallingUtils(gatkOptions: GATKConfig, projectName: Option[String], uppmaxConfig: UppmaxConfig) extends GATKUtils(gatkOptions, projectName, uppmaxConfig) {

  def checkGenotypeConcordance(config: VariantCallingConfig): Seq[File] = {

    val targets =
      config.bamTargets.map(bamTarget => new VariantCallingTarget(config.outputDir,
        bamTarget.bam.getName(),
        gatkOptions.reference,
        Seq(bamTarget),
        gatkOptions.intervalFile,
        config.isLowPass, config.isExome, 1,
        snpGenotypingVcf = gatkOptions.snpGenotypingVcf,
        skipVcfCompression = config.skipVcfCompression))

    for (target <- targets) yield {

      config.qscript.add(
        new UnifiedGenotyperSnpCall(
          target,
          config.testMode,
          config.downsampleFraction,
          config.minimumBaseQuality,
          config.deletions,
          config.noBAQ))

      config.qscript.add(
        new SNPGenotypeConcordance(target))

      target.genotypeConcordance
    }

  }

  /**
   * Utility function for performing the variant calling workflow associated
   * with the HaploTypeCaller
   */
  def variantCallUsingHaplotypeCaller(
    target: VariantCallingTarget,
    config: VariantCallingConfig): Seq[File] = {

    // Call variants separately and merge into one vcf file
    // if the pipeline is set to run a combined analysis.
    if (target.nSamples > 1) {
      val gVcfFiles =
        target.bamTargetList.map(bamTarget => {

          val modifiedTarget =
            new VariantCallingTarget(config.outputDir,
              bamTarget.recalBam.file.getName(),
              gatkOptions.reference,
              Seq(bamTarget),
              gatkOptions.intervalFile,
              config.isLowPass, config.isExome, 1,
              skipVcfCompression = target.skipVcfCompression)

          config.qscript.add(new HaplotypeCallerBase(modifiedTarget, config.testMode, config.downsampleFraction, config.pcrFree, config.minimumBaseQuality))
          modifiedTarget.gVCFFile
        })
      config.qscript.add(new GenotypeGVCF(gVcfFiles, target, config.testMode))
    } else {
      // If the pipeline is setup to run each sample individually, 
      // output one final vcf file per sample.
      config.qscript.add(new HaplotypeCallerBase(target, config.testMode, config.downsampleFraction, config.pcrFree, config.minimumBaseQuality))
      config.qscript.add(new GenotypeGVCF(Seq(target.gVCFFile), target, config.testMode))
    }

    // Evaluate the raw variants (both SNVs and INDELS)
    config.qscript.add(new CombinedEvaluation(target))

    config.qscript.add(new SelectVariantType(target, SNPs, config.testMode))
    config.qscript.add(new SelectVariantType(target, INDELs, config.testMode))

    // Perform recalibration      
    if (!config.noRecal) {
      config.qscript.add(new SnpRecalibration(target))
      config.qscript.add(new SnpCut(target))

      config.qscript.add(new IndelRecalibration(target))
      config.qscript.add(new IndelCut(target))
    }

    config.qscript.add(new SnpEvaluation(target, config.noRecal))
    config.qscript.add(new IndelEvaluation(target, config.noRecal))

    if (!config.noRecal) {
      Seq(
        target.rawCombinedVariants,
        target.recalibratedIndelVCF,
        target.recalibratedSnpVCF,
        target.evalFile,
        target.evalIndelFile)
    }
    else {
      Seq(
        target.rawCombinedVariants,
        target.evalFile,
        target.evalIndelFile)
    }
  }

  /**
   * Utility function for performing the variant calling workflow associated
   * with the UnifiedGenotyper
   */
  def variantCallUsingUnifiedGenotyper(
    target: VariantCallingTarget,
    config: VariantCallingConfig): Seq[File] = {
    if (!config.noIndels) {
      // Indel calling, recalibration and evaulation
      config.qscript.add(new UnifiedGenotyperIndelCall(target, config.testMode, config.downsampleFraction))
      if (!config.noRecal) {
        config.qscript.add(new IndelRecalibration(target))
        config.qscript.add(new IndelCut(target))
      }
      config.qscript.add(new IndelEvaluation(target, config.noRecal))
    }
    // SNP calling, recalibration and evaluation
    config.qscript.add(new UnifiedGenotyperSnpCall(
      target, config.testMode,
      config.downsampleFraction, config.minimumBaseQuality,
      config.deletions, config.noBAQ))

    if (!config.noRecal) {
      config.qscript.add(new SnpRecalibration(target))
      config.qscript.add(new SnpCut(target))
    }

    config.qscript.add(new SnpEvaluation(target, config.noRecal))

    Seq(target.rawSnpVCF, target.rawIndelVCF, target.evalFile, target.evalIndelFile)
  }

  /**
   * Annotated a bunch of vcf files using SnpEff - assumes all input files have
   * a .vcf or .vcf.gz file ending.
   * @param variantFiles The variant files to annotate
   * @return The annotated versions of the files.
   */
  def annotateUsingSnpEff(config: VariantCallingConfig, variantFiles: Seq[File], vcfExtension: String): Seq[File] = {
    val outputVcfExtension =
      if (config.bcftoolsPath.isDefined)
        vcfExtension
      else
        vcfExtension.stripSuffix(".gz")
    for (file <- variantFiles) yield {
      val annotatedFile = GeneralUtils.swapExt(file.getParentFile(), file, vcfExtension, "annotated." + outputVcfExtension)
      config.qscript.add(
        new SnpEff(file, annotatedFile, config))
      annotatedFile
    }
  }

  /**
   * Main entry point for performing variant calling
   * @param config The setup for the variant calling (including the bam files to perform calling on).
   * @return The variants (and variant evaluation files need for the run).
   */
  def performVariantCalling(config: VariantCallingConfig): Seq[File] = {

    // Establish if all samples should be run separately of if they should be
    // run together.
    val targets: Seq[VariantCallingTarget] = (config.runSeparatly, gatkOptions.notHuman) match {
      case (true, false) =>
        config.bamTargets.map(bamTarget => new VariantCallingTarget(config.outputDir,
          bamTarget.recalBam.file.getName(),
          gatkOptions.reference,
          Seq(bamTarget),
          gatkOptions.intervalFile,
          config.isLowPass, config.isExome, 1,
          snpGenotypingVcf = gatkOptions.snpGenotypingVcf,
          skipVcfCompression = config.skipVcfCompression))

      case (true, true) =>
        config.bamTargets.map(bamTarget => new VariantCallingTarget(config.outputDir,
          bamTarget.recalBam.file.getName(),
          gatkOptions.reference,
          Seq(bamTarget),
          gatkOptions.intervalFile,
          config.isLowPass, false, 1,
          snpGenotypingVcf = gatkOptions.snpGenotypingVcf,
          skipVcfCompression = config.skipVcfCompression))

      case (false, true) =>
        Seq(new VariantCallingTarget(config.outputDir,
          projectName.get,
          gatkOptions.reference,
          config.bamTargets,
          gatkOptions.intervalFile,
          config.isLowPass, false, config.bamTargets.size,
          snpGenotypingVcf = gatkOptions.snpGenotypingVcf,
          skipVcfCompression = config.skipVcfCompression))

      case (false, false) =>
        Seq(new VariantCallingTarget(config.outputDir,
          projectName.get,
          gatkOptions.reference,
          config.bamTargets,
          gatkOptions.intervalFile,
          config.isLowPass, config.isExome, config.bamTargets.size,
          snpGenotypingVcf = gatkOptions.snpGenotypingVcf,
          skipVcfCompression = config.skipVcfCompression))
    }

    // Make sure resource files are available if recalibration is to be performed
    if (!config.noRecal) {

      def assertResourceExists(resourceName: String, resource: Option[File]) = {
        assert(resource.isDefined, resourceName + " is not defined. This is needed for variant recalibrations.")
        if (!resource.forall(p => p.exists())) throw new AssertionError("Couldn't find resource: " + resource.get + " This is needed for variant recalibrations.")
      }

      assertResourceExists("hapmap", gatkOptions.hapmap)
      assertResourceExists("omni", gatkOptions.omni)
      assertResourceExists("dbSNP", gatkOptions.dbSNP)
      assertResourceExists("mills", gatkOptions.mills)
      assertResourceExists("1000G", gatkOptions.thousandGenomes)

    }

    val variantAndEvalFiles: Seq[File] =
      targets.flatMap(target => {
        config.variantCaller match {
          case Some(GATKUnifiedGenotyper) => variantCallUsingUnifiedGenotyper(target, config)
          case Some(GATKHaplotypeCaller)  => variantCallUsingHaplotypeCaller(target, config)
        }
      })

    val vcfExtension = targets(0).vcfExtension
    val unannotatedVariantFiles =
      variantAndEvalFiles.filter(f =>
        f.getName().endsWith(vcfExtension) &&
          !f.getName().contains(".genomic.") &&
          (config.noRecal || !f.getName().contains(".raw.")))

    if (config.skipAnnotation)
      variantAndEvalFiles
    else
      annotateUsingSnpEff(config, unannotatedVariantFiles, vcfExtension) ++ variantAndEvalFiles
  }

  def bai(bam: File): File = new File(bam + ".bai")

  /**
   * Class to run the Haplotype caller.
   */
  case class HaplotypeCallerBase(
    t: VariantCallingTarget,
    testMode: Boolean,
    downsampleFraction: Option[Double],
    pcrFree: Option[Boolean],
    minimumBaseQuality: Option[Int])
      extends HaplotypeCaller with CommandLineGATKArgs with FourCoreJob {

    if (testMode)
      this.no_cmdline_in_header = true

    if (downsampleFraction.isDefined && downsampleFraction.get >= 0)
      this.downsample_to_fraction = downsampleFraction
    else
      this.dcov = if (t.isLowpass) { Some(50) } else { Some(250) }

    this.reference_sequence = t.reference
    if (!t.intervals.isEmpty) this.intervals :+= t.intervals.get
    this.scatterCount = gatkOptions.scatterGatherCount.get

    this.nct = gatkOptions.nbrOfThreads

    this.stand_call_conf = Some(30.0)
    this.stand_emit_conf = Some(10.0)

    if (minimumBaseQuality.isDefined && minimumBaseQuality.get >= 0)
      this.min_base_quality_score = Some(min_base_quality_score.get.toByte)

    this.input_file = t.bamTargetList.map( _.recalBam.file )
    this.out = t.gVCFFile

    if (!gatkOptions.dbSNP.isEmpty)
      this.D = gatkOptions.dbSNP.get

    // Make sure we emit a GVCF
    // @TODO make this optional
    this.emitRefConfidence =
      org.broadinstitute.gatk.tools.walkers.haplotypecaller.ReferenceConfidenceMode.GVCF

    this.variant_index_type =
      org.broadinstitute.gatk.utils.variant.GATKVCFIndexType.LINEAR
    this.variant_index_parameter = Some(128000)

    // Make sure to follow recommendations how to analyze 
    // PCR free libraries.
    this.pcr_indel_model =
      if (pcrFree.isDefined && pcrFree.get)
        org.broadinstitute.gatk.tools.walkers.haplotypecaller.PairHMMLikelihoodCalculationEngine.PCR_ERROR_MODEL.NONE
      else
        org.broadinstitute.gatk.tools.walkers.haplotypecaller.PairHMMLikelihoodCalculationEngine.PCR_ERROR_MODEL.CONSERVATIVE

    // This use vector optimization to speed up.
    this.pair_hmm_implementation =
      org.broadinstitute.gatk.utils.pairhmm.PairHMM.HMM_IMPLEMENTATION.LOGLESS_CACHING

    override def jobRunnerJobName = projectName.get + "_HC"
  }

  /**
   * Genotypes the gVCF file from the Haplotype caller
   */
  case class GenotypeGVCF(gVCFFiles: Seq[File], t: VariantCallingTarget, testMode: Boolean) extends GenotypeGVCFs with CommandLineGATKArgs with EightCoreJob {

    if (testMode)
      this.no_cmdline_in_header = true

    this.reference_sequence = t.reference
    this.nt = gatkOptions.nbrOfThreads

    this.variant = gVCFFiles
    this.out = t.rawCombinedVariants

    if (!gatkOptions.dbSNP.isEmpty)
      this.dbsnp = gatkOptions.dbSNP.get

    override def jobRunnerJobName = projectName.get + "_GT_gVCF"
  }

  /**
   * Possible variant types to select
   */
  sealed trait VariantType
  case object SNPs extends VariantType
  case object INDELs extends VariantType

  /**
   * Select a variant type - either SNPs or INDELs
   * Used to separate the classes before  variant recalibration
   */
  case class SelectVariantType(t: VariantCallingTarget, variantType: VariantType, testMode: Boolean) extends SelectVariants with CommandLineGATKArgs with OneCoreJob {

    if (testMode)
      this.no_cmdline_in_header = true

    this.reference_sequence = t.reference
    this.variant = t.rawCombinedVariants

    variantType match {
      case SNPs => {
        this.selectTypeToInclude = Seq(htsjdk.variant.variantcontext.VariantContext.Type.SNP)
        this.out = t.rawSnpVCF
      }
      case INDELs => {
        this.selectTypeToInclude = Seq(htsjdk.variant.variantcontext.VariantContext.Type.INDEL)
        this.out = t.rawIndelVCF
      }
    }
  }

  // 1.) Unified Genotyper Base
  class UnifiedGenotyperBase(t: VariantCallingTarget, testMode: Boolean, downsampleFraction: Option[Double])
      extends UnifiedGenotyper with CommandLineGATKArgs with EightCoreJob {

    if (testMode)
      this.no_cmdline_in_header = true

    if (downsampleFraction.isDefined && downsampleFraction.get >= 0)
      this.downsample_to_fraction = downsampleFraction
    else
      this.dcov = if (t.isLowpass) { Some(50) } else { Some(250) }

    this.reference_sequence = t.reference
    if (!t.intervals.isEmpty) this.intervals :+= t.intervals.get
    this.scatterCount = gatkOptions.scatterGatherCount.get
    this.nt = gatkOptions.nbrOfThreads
    this.stand_call_conf = if (t.isLowpass) { Some(4.0) } else { Some(30.0) }
    this.stand_emit_conf = if (t.isLowpass) { Some(4.0) } else { Some(30.0) }
    this.input_file = t.bamTargetList.map( _.recalBam.file )
    if (!gatkOptions.dbSNP.isEmpty)
      this.D = gatkOptions.dbSNP.get
  }

  // 1a.) Call SNPs with UG
  class UnifiedGenotyperSnpCall(
    t: VariantCallingTarget,
    testMode: Boolean,
    downsampleFraction: Option[Double],
    minimumBaseQuality: Option[Int],
    deletions: Option[Double],
    noBAQ: Boolean)
      extends UnifiedGenotyperBase(t, testMode, downsampleFraction) {

    if (minimumBaseQuality.isDefined && minimumBaseQuality.get >= 0)
      UnifiedGenotyperSnpCall.this.min_base_quality_score = minimumBaseQuality
    if (deletions.isDefined && deletions.get >= 0)
      UnifiedGenotyperSnpCall.this.max_deletion_fraction = deletions

    UnifiedGenotyperSnpCall.this.out = t.rawSnpVCF
    UnifiedGenotyperSnpCall.this.glm = org.broadinstitute.gatk.tools.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.SNP
    UnifiedGenotyperSnpCall.this.baq = if (noBAQ || t.isExome) { org.broadinstitute.gatk.utils.baq.BAQ.CalculationMode.OFF } else { org.broadinstitute.gatk.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY }
    override def jobRunnerJobName = projectName.get + "_UGs"
  }

  // 1b.) Call Indels with UG
  class UnifiedGenotyperIndelCall(
    t: VariantCallingTarget,
    testMode: Boolean,
    downsampleFraction: Option[Double])
      extends UnifiedGenotyperBase(t, testMode, downsampleFraction) {
    UnifiedGenotyperIndelCall.this.out = t.rawIndelVCF
    UnifiedGenotyperIndelCall.this.baq = org.broadinstitute.gatk.utils.baq.BAQ.CalculationMode.OFF
    UnifiedGenotyperIndelCall.this.glm = org.broadinstitute.gatk.tools.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.INDEL
    override def jobRunnerJobName = projectName.get + "_UGi"
  }

  // 2.) Hard Filtering for indels
  class IndelFilter(t: VariantCallingTarget) extends VariantFiltration with CommandLineGATKArgs with OneCoreJob {
    IndelFilter.this.reference_sequence = t.reference
    if (!t.intervals.isEmpty) IndelFilter.this.intervals :+= t.intervals.get
    IndelFilter.this.scatterCount = gatkOptions.scatterGatherCount.get
    IndelFilter.this.V = t.rawIndelVCF
    IndelFilter.this.out = t.filteredIndelVCF
    IndelFilter.this.filterName ++= List("IndelQD", "IndelReadPosRankSum", "IndelFS")
    IndelFilter.this.filterExpression ++= List("QD < 2.0", "ReadPosRankSum < -20.0", "FS > 200.0")

    if (t.nSamples >= 10) {
      IndelFilter.this.filterName ++= List("IndelInbreedingCoeff")
      IndelFilter.this.filterExpression ++= List("InbreedingCoeff < -0.8")
    }

    override def jobRunnerJobName = projectName.get + "_VF"
  }

  class VQSRBase(t: VariantCallingTarget) extends VariantRecalibrator with CommandLineGATKArgs with EightCoreJob {
    this.nt = gatkOptions.nbrOfThreads
    this.reference_sequence = t.reference
    if (!t.intervals.isEmpty) this.intervals :+= t.intervals.get
    this.allPoly = true
    this.tranche ++= List("100.0", "99.9", "99.0", "90.0")
  }

  class SnpRecalibration(t: VariantCallingTarget) extends VQSRBase(t) {

    this.input :+= t.rawSnpVCF

    //  From best practice: -an QD -an MQ -an MQRankSum -an ReadPosRankSum
    // -an FS -an SOR -an DP (note that this added below - only for WG)
    // (-an InbreedingCoeff)
    this.use_annotation ++= {
      val standardAnnotations =
        List("QD", "MQ", "MQRankSum", "ReadPosRankSum", "FS", "SOR")
      val extraAnnotations =
        if (t.nSamples >= 10) List("InbreedingCoeff") else List()
      standardAnnotations ++ extraAnnotations
    }

    // Whole genome case
    if (!t.isExome) {
      this.resource :+= new TaggedFile(gatkOptions.hapmap.get, "known=false,training=true,truth=true,prior=15.0")
      this.resource :+= new TaggedFile(gatkOptions.omni.get, "known=false,training=true,truth=true,prior=12.0")
      this.resource :+= new TaggedFile(gatkOptions.thousandGenomes.get, "known=false,training=true,truth=false,prior=10.0")
      this.resource :+= new TaggedFile(gatkOptions.dbSNP.get, "known=true,training=false,truth=false,prior=2.0")

      // Don't use the DP annotation for exome samples.
      this.use_annotation ++= List("DP")
    } else // exome specific parameters
    {
      this.mG = Some(6)

      if (t.nSamples <= 30) { // very few exome samples means very few variants
        this.mG = Some(4)
      }
    }

    this.tranches_file = t.tranchesSnpFile
    this.recal_file = t.recalSnpFile

    this.rscript_file = t.vqsrSnpRscript
    this.mode = org.broadinstitute.gatk.tools.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    override def jobRunnerJobName = projectName.get + "_VQSRs"
  }

  class IndelRecalibration(t: VariantCallingTarget) extends VQSRBase(t) {

    // Note that for indel recalication the same settings are used both for WGS and Exome Seq.

    this.input :+= t.rawIndelVCF
    this.resource :+= new TaggedFile(gatkOptions.mills.get, "known=true,training=true,truth=true,prior=12.0")

    // From best practice: -an QD -an DP -an FS -an SOR -an ReadPosRankSum -an MQRankSum -an InbreedingCoeff
    this.use_annotation ++= {
      val standardAnnotations = List("QD", "DP", "FS", "SOR", "ReadPosRankSum", "MQRankSum")
      val extraAnnotations = if (t.nSamples >= 10) List("InbreedingCoeff") else List()
      standardAnnotations ++ extraAnnotations
    }

    this.mG = Some(4)
    this.std = Some(10)

    this.tranches_file = t.tranchesIndelFile
    this.recal_file = t.recalIndelFile
    this.rscript_file = t.vqsrIndelRscript
    this.mode = org.broadinstitute.gatk.tools.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.INDEL
    override def jobRunnerJobName = projectName.get + "_VQSRi"
  }

  // 4.) Apply the recalibration table to the appropriate tranches
  class ApplyVQSRBase(t: VariantCallingTarget) extends ApplyRecalibration with CommandLineGATKArgs with OneCoreJob {
    this.reference_sequence = t.reference
    if (!t.intervals.isEmpty) this.intervals :+= t.intervals.get
  }

  class SnpCut(t: VariantCallingTarget) extends ApplyVQSRBase(t) {

    this.input :+= t.rawSnpVCF
    this.tranches_file = t.tranchesSnpFile
    this.recal_file = t.recalSnpFile

    // By default this is 99.0
    // this.ts_filter_level = t.snpTrancheTarget
    this.mode = org.broadinstitute.gatk.tools.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    this.out = t.recalibratedSnpVCF
    override def jobRunnerJobName = projectName.get + "_AVQSRs"
  }

  class IndelCut(t: VariantCallingTarget) extends ApplyVQSRBase(t) {
    this.input :+= t.rawIndelVCF
    this.tranches_file = t.tranchesIndelFile
    this.recal_file = t.recalIndelFile

    // By default this is 99.0
    //this.ts_filter_level = t.indelTranchTarget
    this.mode = org.broadinstitute.gatk.tools.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.INDEL
    this.out = t.recalibratedIndelVCF
    override def jobRunnerJobName = projectName.get + "_AVQSRi"
  }

  // 5.) Variant Evaluation Base(OPTIONAL)
  class EvalBase(t: VariantCallingTarget) extends VariantEval with CommandLineGATKArgs with OneCoreJob {
    if (!gatkOptions.hapmap.isEmpty)
      this.comp :+= new TaggedFile(gatkOptions.hapmap.get, "hapmap")
    if (!gatkOptions.dbSNP.isEmpty)
      this.D = gatkOptions.dbSNP.get
    this.reference_sequence = t.reference
    if (!t.intervals.isEmpty) this.intervals :+= t.intervals.get
  }

  class CombinedEvaluation(t: VariantCallingTarget) extends EvalBase(t) {
    this.eval :+= t.rawCombinedVariants
    this.out = t.combinedEvalFile

    if (t.snpGenotypingVcf.isDefined)
      this.comp :+= t.snpGenotypingVcf.get

    override def jobRunnerJobName = projectName.get + "_varianteval_raw_variants"
  }

  // 5a.) SNP Evaluation (OPTIONAL) based on the cut vcf
  class SnpEvaluation(t: VariantCallingTarget, noRecal: Boolean) extends EvalBase(t) {
    if (!noRecal)
      this.eval :+= t.recalibratedSnpVCF
    this.eval :+= t.rawSnpVCF
    if (t.snpGenotypingVcf.isDefined)
      this.comp :+= t.snpGenotypingVcf.get
    this.out = t.evalFile
    override def jobRunnerJobName = projectName.get + "_VEs"
  }

  // 5b.) Indel Evaluation (OPTIONAL)
  class IndelEvaluation(t: VariantCallingTarget, noRecal: Boolean) extends EvalBase(t) {
    if (!noRecal)
      this.eval :+= t.recalibratedIndelVCF
    this.eval :+= t.rawIndelVCF

    this.out = t.evalIndelFile
    override def jobRunnerJobName = projectName.get + "_VEi"
  }

  class SNPGenotypeConcordance(t: VariantCallingTarget) extends GenotypeConcordance with CommandLineGATKArgs with OneCoreJob {
    this.eval = t.rawSnpVCF
    this.comp = TaggedFile(t.snpGenotypingVcf.get, "chip_genotypes")
    this.out = t.genotypeConcordance
  }

  case class SnpEff(@Input input: File, @Output output: File, config: VariantCallingConfig) extends CommandLineFunction with TwoCoreJob {

    // If the path to the snpEffConfig has not been defined then assume that it
    // lays one level down from the snpEff bash-wrapper script.
    val snpEffConfig =
      if (config.snpEffConfigPath.isDefined)
        config.snpEffConfigPath.get.getAbsolutePath()
      else
        config.snpEffPath.get.getAbsolutePath().stripSuffix("snpEff") +
          "/../snpEff.config"

    // If the path to bcftools has not been defined, skip compression of the output vcf file
    val outputRedirect =
      if (config.bcftoolsPath.isDefined && !config.skipVcfCompression) {
        "| " + config.bcftoolsPath.get.getAbsolutePath() + " view -Oz - > " + output.getAbsolutePath() +
          "; " + config.bcftoolsPath.get.getAbsolutePath() + " index -t " + output.getAbsolutePath()
      }
      else
        "> " + output.getAbsolutePath()

    override def commandLine =
      config.snpEffPath.get.getAbsolutePath() + " " +
        // Explicitly pass the JVM parameters to snpEff since it wraps the java command
        " -Xmx" + this.memoryLimit.get.toInt.toString + "G " +
        " -c " + snpEffConfig + " " +
        " -csvStats " +
        " -stats " + output.getAbsolutePath() + ".snpEff.summary.csv " +
        config.snpEffReference.get + " " +
        input.getAbsolutePath() + " " + outputRedirect
  }

}
