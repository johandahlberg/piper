package molmed.utils

import java.io.File

import org.broadinstitute.gatk.tools.walkers.indels.IndelRealigner.ConsensusDeterminationModel
import org.broadinstitute.gatk.queue.extensions.gatk.BaseRecalibrator
import org.broadinstitute.gatk.queue.extensions.gatk.CommandLineGATK
import org.broadinstitute.gatk.queue.extensions.gatk.IndelRealigner
import org.broadinstitute.gatk.queue.extensions.gatk.PrintReads
import org.broadinstitute.gatk.queue.extensions.gatk.RealignerTargetCreator
import org.broadinstitute.gatk.utils.baq.BAQ.CalculationMode

/**
 * Commandline wappers for GATK programs.
 */
class GATKUtils(gatkOptions: GATKConfig, projectName: Option[String], uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig) {

  // General arguments to GATK walkers
  trait CommandLineGATKArgs extends CommandLineGATK {
    this.reference_sequence = gatkOptions.reference
  }

  case class DepthOfCoverage(inBam: File, outputDir: File) extends org.broadinstitute.gatk.queue.extensions.gatk.DepthOfCoverage with CommandLineGATKArgs with OneCoreJob {
    this.input_file = Seq(inBam)
    this.out = outputDir
    if (!gatkOptions.intervalFile.isEmpty) this.intervals :+= gatkOptions.intervalFile.get
    this.isIntermediate = false
    override def jobRunnerJobName = projectName.get + "_depth_of_coverage"

    this.omitBaseOutput = true
  }

  case class target(inBams: Seq[File], outIntervals: File, @Argument cleanModelEnum: ConsensusDeterminationModel) extends RealignerTargetCreator with CommandLineGATKArgs with TwoCoreJob {

    this.num_threads = gatkOptions.nbrOfThreads

    if (cleanModelEnum != ConsensusDeterminationModel.KNOWNS_ONLY)
      this.input_file = inBams
    this.out = outIntervals
    this.mismatchFraction = Some(0.0)
    if (!gatkOptions.dbSNP.isEmpty)
      this.known ++= Seq(gatkOptions.dbSNP.get)
    if (!gatkOptions.indels.isEmpty)
      this.known ++= gatkOptions.indels.get

    this.scatterCount = gatkOptions.scatterGatherCount.get
    override def jobRunnerJobName = projectName.get + "_targets"

  }

  case class clean(inBams: Seq[File], tIntervals: File, outBam: File,
                   @Argument cleanModelEnum: ConsensusDeterminationModel,
                   testMode: Boolean, asIntermediate: Boolean = true) extends IndelRealigner with CommandLineGATKArgs with OneCoreJob {

    this.isIntermediate = asIntermediate

    this.bam_compression = Some(5)

    this.input_file = inBams
    this.targetIntervals = tIntervals
    this.out = outBam
    if (!gatkOptions.dbSNP.isEmpty)
      this.known ++= Seq(gatkOptions.dbSNP.get)
    if (!gatkOptions.indels.isEmpty)
      this.known ++= gatkOptions.indels.get
    this.consensusDeterminationModel = cleanModelEnum
    this.noPGTag = testMode;
    this.scatterCount = gatkOptions.scatterGatherCount.get
    override def jobRunnerJobName = projectName.get + "_clean"

  }

  case class cov(inBam: File, outRecalFile: File, @Argument defaultPlatform: String, inRecalFile: Option[File] = None) extends BaseRecalibrator with CommandLineGATKArgs with EightCoreJob {

    this.num_cpu_threads_per_data_thread = gatkOptions.nbrOfThreads

    if (!gatkOptions.dbSNP.isEmpty)
      this.knownSites ++= Seq(gatkOptions.dbSNP.get)
    this.covariate ++= Seq("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "ContextCovariate")
    this.input_file :+= inBam
    this.disable_indel_quals = false
    this.out = outRecalFile
    if (!defaultPlatform.isEmpty) this.default_platform = defaultPlatform
    if (!gatkOptions.intervalFile.isEmpty) this.intervals :+= gatkOptions.intervalFile.get

    this.scatterCount = gatkOptions.scatterGatherCount.get
    if (!inRecalFile.isEmpty)
      this.BQSR = inRecalFile.get
    override def jobRunnerJobName = projectName.get + "_cov"

  }

  case class recal(inBam: File, inRecalFile: File, outBam: File, asIntermediate: Boolean = false) extends PrintReads with CommandLineGATKArgs with FourCoreJob {

    this.isIntermediate = asIntermediate

    this.input_file :+= inBam

    this.bam_compression = Some(5)

    this.BQSR = inRecalFile
    // Disable the insertion and deletion qualities (BI and BD tags)
    this.disable_indel_quals = true
    // Emit the original qualities (OQ tag)
    this.emit_original_quals = true
    this.baq = CalculationMode.CALCULATE_AS_NECESSARY
    this.out = outBam
    this.scatterCount = gatkOptions.scatterGatherCount.get
    this.num_cpu_threads_per_data_thread = gatkOptions.nbrOfThreads
    override def jobRunnerJobName = projectName.get + "_recal"

  }

}