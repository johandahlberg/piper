package molmed.utils

import org.broadinstitute.sting.queue.extensions.gatk.UnifiedGenotyper
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk.VariantFiltration
import org.broadinstitute.sting.queue.extensions.gatk.VariantRecalibrator
import org.broadinstitute.sting.queue.extensions.gatk.TaggedFile
import org.broadinstitute.sting.queue.extensions.gatk.ApplyRecalibration
import org.broadinstitute.sting.queue.extensions.gatk.VariantEval
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk.HaplotypeCaller
import org.broadinstitute.sting.queue.extensions.gatk.GenotypeGVCFs

/**
 * Wrapping case classed and functions for doing variant calling using the GATK.
 *
 * Contains code both to use the Unified genotyper and the Haplotype caller.
 */
class VariantCallingUtils(gatkOptions: GATKConfig, projectName: Option[String], uppmaxConfig: UppmaxConfig) extends GATKUtils(gatkOptions, projectName, uppmaxConfig) {

  def performVariantCalling(config: VariantCallingConfig): Seq[File] = {

    /**
     * Utility function for performing the variant calling workflow associated
     * with the UnifiedGenotyper
     */
    def variantCallUsingUnifiedGenotyper(target: VariantCallingTarget) = {
      if (!config.noIndels) {
        // Indel calling, recalibration and evaulation
        config.qscript.add(new UnifiedGenotyperIndelCall(target, config.testMode, config.downsampleFraction))
        if (!config.noRecal) {
          config.qscript.add(new IndelRecalibration(target))
          config.qscript.add(new IndelCut(target))
          config.qscript.add(new IndelEvaluation(target))
        }
      }
      // SNP calling, recalibration and evaluation
      config.qscript.add(new UnifiedGenotyperSnpCall(
        target, config.testMode,
        config.downsampleFraction, config.minimumBaseQuality,
        config.deletions, config.noBAQ))
      if (!config.noRecal) {
        config.qscript.add(new SnpRecalibration(target))
        config.qscript.add(new SnpCut(target))
        config.qscript.add(new SnpEvaluation(target))
      }
    }

    /**
     * Utility function for performing the variant calling workflow associated
     * with the HaploTypeCaller
     */
    def variantCallUsingHaplotypeCaller(target: VariantCallingTarget) = {

      // Call variants separately and merge into one vcf file
      // if the pipeline is set to run a combined analysis.
      if (target.nSamples > 1) {
        val gVcfFiles =
          target.bamList.map(bam => {

            val modifiedTarget =
              new VariantCallingTarget(config.outputDir,
                bam.getName(),
                gatkOptions.reference,
                Seq(bam),
                gatkOptions.intervalFile,
                config.isLowPass, config.isExome, 1)

            config.qscript.add(new HaplotypeCallerBase(modifiedTarget, config.testMode, config.downsampleFraction, config.pcrFree))
            modifiedTarget.gVCFFile
          })
        config.qscript.add(new GenotypeGVCF(gVcfFiles, target))

      } // If the pipeline is setup to run each sample individually, 
      // output one final vcf file per sample.
      else {
        config.qscript.add(new HaplotypeCallerBase(target, config.testMode, config.downsampleFraction, config.pcrFree))
        config.qscript.add(new GenotypeGVCF(Seq(target.gVCFFile), target))
      }

      //@TODO Processed with same workflow as for UnifiedGenotyper

      // @TODO Figure out if this actually needs to be done now!
      // Or if we can skip this for NGI/1KSG
      // Perform recalibration      
      //      if (!config.noRecal) {
      //        config.qscript.add(new SnpRecalibration(target))
      //        config.qscript.add(new SnpCut(target))
      //        config.qscript.add(new SnpEvaluation(target))
      //      }
    }

    // Establish if all samples should be run separately of if they should be
    // run together.
    val targets: Seq[VariantCallingTarget] = (config.runSeparatly, config.notHuman) match {
      case (true, false) =>
        config.bams.map(bam => new VariantCallingTarget(config.outputDir,
          bam.getName(),
          gatkOptions.reference,
          Seq(bam),
          gatkOptions.intervalFile,
          config.isLowPass, config.isExome, 1))

      case (true, true) =>
        config.bams.map(bam => new VariantCallingTarget(config.outputDir,
          bam.getName(),
          gatkOptions.reference,
          Seq(bam),
          gatkOptions.intervalFile,
          config.isLowPass, false, 1))

      case (false, true) =>
        Seq(new VariantCallingTarget(config.outputDir,
          projectName.get,
          gatkOptions.reference,
          config.bams,
          gatkOptions.intervalFile,
          config.isLowPass, false, config.bams.size))

      case (false, false) =>
        Seq(new VariantCallingTarget(config.outputDir,
          projectName.get,
          gatkOptions.reference,
          config.bams,
          gatkOptions.intervalFile,
          config.isLowPass, config.isExome, config.bams.size))
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

    }

    for (target <- targets) {
      config.variantCaller match {
        case GATKUnifiedGenotyper => variantCallUsingUnifiedGenotyper(target)
        case GATKHaplotypeCaller  => variantCallUsingHaplotypeCaller(target)
      }
    }

    //@TODO 
    Seq.empty
  }

  def bai(bam: File): File = new File(bam + ".bai")

  /**
   * Class to run the Haplotype caller.
   */
  case class HaplotypeCallerBase(
    t: VariantCallingTarget,
    testMode: Boolean,
    downsampleFraction: Option[Double],
    pcrFree: Option[Boolean])
      extends HaplotypeCaller with CommandLineGATKArgs with SixteenCoreJob {

    if (testMode)
      this.no_cmdline_in_header = true

    if (downsampleFraction.isDefined && downsampleFraction.get >= 0)
      this.downsample_to_fraction = downsampleFraction
    else
      this.dcov = if (t.isLowpass) { Some(50) } else { Some(250) }

    this.reference_sequence = t.reference
    if (!t.intervals.isEmpty) this.intervals :+= t.intervals.get
    this.scatterCount = gatkOptions.scatterGatherCount.get

    //@TODO figure out if this is reasonable or not!
    this.nct = Some(16)

    this.stand_call_conf = Some(30.0)
    this.stand_emit_conf = Some(10.0)

    this.input_file = t.bamList
    this.out = t.gVCFFile

    if (!gatkOptions.dbSNP.isEmpty)
      this.D = gatkOptions.dbSNP.get

    // Make sure we emit a GVCF
    // @TODO make this optional
    this.emitRefConfidence =
      org.broadinstitute.sting.gatk.walkers.haplotypecaller.HaplotypeCaller.ReferenceConfidenceMode.GVCF

    this.variant_index_type =
      org.broadinstitute.sting.utils.variant.GATKVCFIndexType.LINEAR
    this.variant_index_parameter = Some(128000)

    // Make sure to follow recommendations how to analyze 
    // PCR free libraries.
    this.pcr_indel_model =
      if (pcrFree.isDefined && pcrFree.get)
        org.broadinstitute.sting.gatk.walkers.haplotypecaller.PairHMMLikelihoodCalculationEngine.PCR_ERROR_MODEL.NONE
      else
        org.broadinstitute.sting.gatk.walkers.haplotypecaller.PairHMMLikelihoodCalculationEngine.PCR_ERROR_MODEL.CONSERVATIVE

    // This use vector optimization to speed up.
    this.pair_hmm_implementation =
      org.broadinstitute.sting.utils.pairhmm.PairHMM.HMM_IMPLEMENTATION.LOGLESS_CACHING

    override def jobRunnerJobName = projectName.get + "_HC"
  }

  class GenotypeGVCF(gVCFFiles: Seq[File], t: VariantCallingTarget) extends GenotypeGVCFs with CommandLineGATKArgs with EightCoreJob {
    this.reference_sequence = t.reference
    this.nt = gatkOptions.nbrOfThreads

    this.variant = gVCFFiles
    this.out = t.rawCombinedVariants

    if (!gatkOptions.dbSNP.isEmpty)
      this.dbsnp = gatkOptions.dbSNP.get

    override def jobRunnerJobName = projectName.get + "_GT_gVCF"
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
    this.input_file = t.bamList
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
    UnifiedGenotyperSnpCall.this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.SNP
    UnifiedGenotyperSnpCall.this.baq = if (noBAQ || t.isExome) { org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF } else { org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY }
    override def jobRunnerJobName = projectName.get + "_UGs"
  }

  // 1b.) Call Indels with UG
  class UnifiedGenotyperIndelCall(
    t: VariantCallingTarget,
    testMode: Boolean,
    downsampleFraction: Option[Double])
      extends UnifiedGenotyperBase(t, testMode, downsampleFraction) {
    UnifiedGenotyperIndelCall.this.out = t.rawIndelVCF
    UnifiedGenotyperIndelCall.this.baq = org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF
    UnifiedGenotyperIndelCall.this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.INDEL
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
    this.tranche ++= List("100.0", "99.9", "99.5", "99.3", "99.0", "98.9", "98.8", "98.5", "98.4", "98.3", "98.2", "98.1", "98.0", "97.9", "97.8", "97.5", "97.0", "95.0", "90.0")
  }

  class SnpRecalibration(t: VariantCallingTarget) extends VQSRBase(t) {

    this.input :+= t.rawSnpVCF

    //  From best practice: -an QD -an MQRankSum -an ReadPosRankSum -an FS -an DP
    this.use_annotation ++= List("QD", "HaplotypeScore", "MQRankSum", "ReadPosRankSum", "MQ", "FS", "DP")
    if (t.nSamples >= 10)
      this.use_annotation ++= List("InbreedingCoeff") // InbreedingCoeff is a population-wide statistic that requires at least 10 samples to calculate

    // Whole genome case
    if (!t.isExome) {
      this.resource :+= new TaggedFile(gatkOptions.hapmap.get, "known=false,training=true,truth=true,prior=15.0")
      this.resource :+= new TaggedFile(gatkOptions.omni.get, "known=false,training=true,truth=true,prior=12.0")
      this.resource :+= new TaggedFile(gatkOptions.dbSNP.get, "known=true,training=false,truth=false,prior=6.0")

      this.use_annotation ++= List("DP")
    } else // exome specific parameters
    {
      this.mG = Some(6)

      this.resource :+= new TaggedFile(gatkOptions.hapmap.get, "known=false,training=true,truth=true,prior=15.0")
      this.resource :+= new TaggedFile(gatkOptions.omni.get, "known=false,training=true,truth=false,prior=12.0")
      this.resource :+= new TaggedFile(gatkOptions.dbSNP.get, "known=true,training=false,truth=false,prior=6.0")

      if (t.nSamples <= 30) { // very few exome samples means very few variants
        this.mG = Some(4)
      }
    }

    this.tranches_file = t.tranchesSnpFile
    this.recal_file = t.recalSnpFile

    this.rscript_file = t.vqsrSnpRscript
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    override def jobRunnerJobName = projectName.get + "_VQSRs"
  }

  class IndelRecalibration(t: VariantCallingTarget) extends VQSRBase(t) {

    // Note that for indel recalication the same settings are used both for WGS and Exome Seq.

    this.input :+= t.rawIndelVCF
    this.resource :+= new TaggedFile(gatkOptions.mills.get, "known=true,training=true,truth=true,prior=12.0")

    // From best practice: -an DP -an FS -an ReadPosRankSum -an MQRankSum
    this.use_annotation ++= List("QD", "ReadPosRankSum", "FS", "DP", "MQRankSum")

    this.mG = Some(4)
    this.std = Some(10)

    if (t.nSamples >= 10)
      this.use_annotation ++= List("InbreedingCoeff") // InbreedingCoeff is a population-wide statistic that requires at least 10 samples to calculate

    this.tranches_file = t.tranchesIndelFile
    this.recal_file = t.recalIndelFile
    this.rscript_file = t.vqsrIndelRscript
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.INDEL
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
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    this.out = t.recalibratedSnpVCF
    override def jobRunnerJobName = projectName.get + "_AVQSRs"
  }

  class IndelCut(t: VariantCallingTarget) extends ApplyVQSRBase(t) {
    this.input :+= t.rawIndelVCF
    this.tranches_file = t.tranchesIndelFile
    this.recal_file = t.recalIndelFile

    // By default this is 99.0
    //this.ts_filter_level = t.indelTranchTarget
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.INDEL
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

  // 5a.) SNP Evaluation (OPTIONAL) based on the cut vcf
  class SnpEvaluation(t: VariantCallingTarget) extends EvalBase(t) {
    // TODO Setup resonable comparisson file
    //if (t.reference == b37 || t.reference == hg19) this.comp :+= new TaggedFile( omni_b37, "omni" )
    this.eval :+= t.recalibratedSnpVCF
    this.out = t.evalFile
    override def jobRunnerJobName = projectName.get + "_VEs"
  }

  // 5b.) Indel Evaluation (OPTIONAL)
  class IndelEvaluation(t: VariantCallingTarget) extends EvalBase(t) {
    this.eval :+= t.recalibratedIndelVCF

    // TODO Setup resonable comparisson file
    //this.comp :+= new TaggedFile(indelGoldStandardCallset, "indelGS" )
    this.noEV = true
    //TODO Check, if no eval modules are assigned, the standard ones are used.
    //this.evalModule = List("CompOverlap", "CountVariants", "TiTvVariantEvaluator", "ValidationReport", "IndelStatistics")
    this.out = t.evalIndelFile
    override def jobRunnerJobName = projectName.get + "_VEi"
  }

}
