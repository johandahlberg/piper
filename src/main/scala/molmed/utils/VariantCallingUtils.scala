package molmed.utils

import org.broadinstitute.sting.queue.extensions.gatk.UnifiedGenotyper
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk.VariantFiltration
import org.broadinstitute.sting.queue.extensions.gatk.VariantRecalibrator
import org.broadinstitute.sting.queue.extensions.gatk.TaggedFile
import org.broadinstitute.sting.queue.extensions.gatk.ApplyRecalibration
import org.broadinstitute.sting.queue.extensions.gatk.VariantEval

class VariantCallingUtils(gatkOptions: GATKOptions, projectName: Option[String], projId: String, uppmaxQoSFlag: Option[String]) extends GATKUtils(gatkOptions, projectName, projId, uppmaxQoSFlag) {

  def bai(bam: File): File = new File(bam + ".bai")

  // 1.) Unified Genotyper Base
  class GenotyperBase(t: VariantCallingTarget, testMode: Boolean, downsampleFraction: Option[Double]) extends UnifiedGenotyper with CommandLineGATKArgs {

    if (testMode)
      this.no_cmdline_in_header = true

    if (downsampleFraction.get != -1.0)
      this.downsample_to_fraction = downsampleFraction
    else
      this.dcov = if (t.isLowpass) { Some(50) } else { Some(250) }

    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
    this.scatterCount = gatkOptions.scatterGatherCount.get
    this.nt = gatkOptions.nbrOfThreads
    this.stand_call_conf = if (t.isLowpass) { Some(4.0) } else { Some(30.0) }
    this.stand_emit_conf = if (t.isLowpass) { Some(4.0) } else { Some(30.0) }
    this.input_file :+= t.bamList
    if (t.resources.dbsnp.exists())
      this.D = t.resources.dbsnp
  }

  // 1a.) Call SNPs with UG
  class snpCall(t: VariantCallingTarget, testMode: Boolean, downsampleFraction: Option[Double], minimumBaseQuality: Int, deletions: Double, noBAQ: Boolean) extends GenotyperBase(t, testMode, downsampleFraction) {

    if (minimumBaseQuality >= 0)
      this.min_base_quality_score = Some(minimumBaseQuality)
    if (deletions >= 0)
      this.max_deletion_fraction = Some(deletions)

    this.out = t.rawSnpVCF
    this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.SNP
    this.baq = if (noBAQ || t.isExome) { org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF } else { org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY }
    override def jobRunnerJobName = projectName.get + "_UGs"
  }

  // 1b.) Call Indels with UG
  class indelCall(t: VariantCallingTarget, testMode: Boolean, downsampleFraction: Option[Double]) extends GenotyperBase(t, testMode, downsampleFraction) {
    this.out = t.rawIndelVCF
    this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.INDEL
    this.baq = org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF
    override def jobRunnerJobName = projectName.get + "_UGi"
  }

  // 2.) Hard Filtering for indels
  class indelFilter(t: VariantCallingTarget) extends VariantFiltration with CommandLineGATKArgs {
    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
    this.scatterCount = gatkOptions.scatterGatherCount.get
    this.V = t.rawIndelVCF
    this.out = t.filteredIndelVCF
    this.filterName ++= List("IndelQD", "IndelReadPosRankSum", "IndelFS")
    this.filterExpression ++= List("QD < 2.0", "ReadPosRankSum < -20.0", "FS > 200.0")

    if (t.nSamples >= 10) {
      this.filterName ++= List("IndelInbreedingCoeff")
      this.filterExpression ++= List("InbreedingCoeff < -0.8")
    }

    override def jobRunnerJobName = projectName.get + "_VF"
  }

  class VQSRBase(t: VariantCallingTarget) extends VariantRecalibrator with CommandLineGATKArgs {
    this.nt = gatkOptions.nbrOfThreads
    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
    this.allPoly = true
    this.tranche ++= List("100.0", "99.9", "99.5", "99.3", "99.0", "98.9", "98.8", "98.5", "98.4", "98.3", "98.2", "98.1", "98.0", "97.9", "97.8", "97.5", "97.0", "95.0", "90.0")
  }

  class snpRecal(t: VariantCallingTarget) extends VQSRBase(t) {

    this.input :+= t.rawSnpVCF

    // Check resource files are present
    assert(t.resources.hapmap.exists(), "Couln't locate hapmap file: " + t.resources.hapmap)
    assert(t.resources.omni.exists(), "Couln't locate omni file: " + t.resources.omni)
    assert(t.resources.dbsnp.exists(), "Couln't locate dbSNP file: " + t.resources.dbsnp)

    //  From best practice: -an QD -an MQRankSum -an ReadPosRankSum -an FS -an DP
    this.use_annotation ++= List("QD", "HaplotypeScore", "MQRankSum", "ReadPosRankSum", "MQ", "FS", "DP")
    if (t.nSamples >= 10)
      this.use_annotation ++= List("InbreedingCoeff") // InbreedingCoeff is a population-wide statistic that requires at least 10 samples to calculate

    // Whole genome case
    if (!t.isExome) {
      this.resource :+= new TaggedFile(t.resources.hapmap, "known=false,training=true,truth=true,prior=15.0")
      this.resource :+= new TaggedFile(t.resources.omni, "known=false,training=true,truth=true,prior=12.0")
      this.resource :+= new TaggedFile(t.resources.dbsnp, "known=true,training=false,truth=false,prior=6.0")

      this.use_annotation ++= List("DP")
    } else // exome specific parameters
    {
      this.mG = Some(6)

      this.resource :+= new TaggedFile(t.resources.hapmap, "known=false,training=true,truth=true,prior=15.0")
      this.resource :+= new TaggedFile(t.resources.omni, "known=false,training=true,truth=false,prior=12.0")
      this.resource :+= new TaggedFile(t.resources.dbsnp, "known=true,training=false,truth=false,prior=6.0")

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

  class indelRecal(t: VariantCallingTarget) extends VQSRBase(t) {

    // Note that for indel recalication the same settings are used both for WGS and Exome Seq.

    this.input :+= t.rawIndelVCF
    assert(t.resources.mills.exists(), "Couln't locate mills file: " + t.resources.mills)
    this.resource :+= new TaggedFile(t.resources.mills, "known=true,training=true,truth=true,prior=12.0")

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
  class applyVQSRBase(t: VariantCallingTarget) extends ApplyRecalibration with CommandLineGATKArgs {
    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
  }

  class snpCut(t: VariantCallingTarget) extends applyVQSRBase(t) {

    this.input :+= t.rawSnpVCF
    this.tranches_file = t.tranchesSnpFile
    this.recal_file = t.recalSnpFile

    // By default this is 99.0
    // this.ts_filter_level = t.snpTrancheTarget
    this.mode = org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    this.out = t.recalibratedSnpVCF
    override def jobRunnerJobName = projectName.get + "_AVQSRs"
  }

  class indelCut(t: VariantCallingTarget) extends applyVQSRBase(t) {
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
  class EvalBase(t: VariantCallingTarget) extends VariantEval with CommandLineGATKArgs {
    if (t.resources.hapmap.exists())
      this.comp :+= new TaggedFile(t.resources.hapmap, "hapmap")
    if (t.resources.dbsnp.exists())
      this.D = t.resources.dbsnp
    this.reference_sequence = t.reference
    if (t.intervals != null) this.intervals :+= t.intervals
  }

  // 5a.) SNP Evaluation (OPTIONAL) based on the cut vcf
  class snpEvaluation(t: VariantCallingTarget) extends EvalBase(t) {
    // TODO Setup resonable comparisson file
    //if (t.reference == b37 || t.reference == hg19) this.comp :+= new TaggedFile( omni_b37, "omni" )
    this.eval :+= t.recalibratedSnpVCF
    this.out = t.evalFile
    override def jobRunnerJobName = projectName.get + "_VEs"
  }

  // 5b.) Indel Evaluation (OPTIONAL)
  class indelEvaluation(t: VariantCallingTarget) extends EvalBase(t) {
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