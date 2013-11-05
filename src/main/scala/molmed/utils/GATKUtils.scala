package molmed.utils

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk.CommandLineGATK
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.utils.baq.BAQ.CalculationMode
import org.broadinstitute.sting.gatk.walkers.indels.IndelRealigner.ConsensusDeterminationModel

case class GATKOptions(
  reference: File,
  nbrOfThreads: Option[Int] = Some(8),
  scatterGatherCount: Option[Int] = Some(1),
  intervalFile: Option[File],
  dbSNP: Option[Seq[File]],
  indels: Option[Seq[File]])

class GATKUtils(gatkOptions: GATKOptions, projectName: Option[String], projId: String, getUppmaxQosFlag: Option[String]) extends UppmaxUtils(projectName, projId, getUppmaxQosFlag) {

  // General arguments to GATK walkers
  trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {
    this.reference_sequence = gatkOptions.reference
  }

  case class DepthOfCoverage(inBam: File, outputDir: File) extends org.broadinstitute.sting.queue.extensions.gatk.DepthOfCoverage with NineGbRamJobs {
    this.input_file = Seq(inBam)
    this.out = outputDir
    if (!gatkOptions.intervalFile.isEmpty) this.intervals :+= gatkOptions.intervalFile.get
    this.isIntermediate = false
    this.analysisName = "DepthOfCoverage"
    this.jobName = "DepthOfCoverage"
    this.omitBaseOutput = true
  }

  case class target(inBams: Seq[File], outIntervals: File, cleanModelEnum: ConsensusDeterminationModel) extends RealignerTargetCreator with CommandLineGATKArgs {

    this.num_threads = gatkOptions.nbrOfThreads

    if (cleanModelEnum != ConsensusDeterminationModel.KNOWNS_ONLY)
      this.input_file = inBams
    this.out = outIntervals
    this.mismatchFraction = Some(0.0)
    if (!gatkOptions.dbSNP.isEmpty)
      this.known ++= gatkOptions.dbSNP.get
    if (!gatkOptions.indels.isEmpty)
      this.known ++= gatkOptions.indels.get
    this.scatterCount = gatkOptions.scatterGatherCount.get
    this.analysisName = projId + "_targets"
    this.jobName = projId + "_targets"
  }

  case class clean(inBams: Seq[File], tIntervals: File, outBam: File, cleanModelEnum: ConsensusDeterminationModel, testMode: Boolean) extends IndelRealigner with CommandLineGATKArgs {

    //TODO This should probably be a core job since it does not support parallel exection.         

    this.input_file = inBams
    this.targetIntervals = tIntervals
    this.out = outBam
    if (!gatkOptions.dbSNP.isEmpty)
      this.known ++= gatkOptions.dbSNP.get
    if (!gatkOptions.indels.isEmpty)
      this.known ++= gatkOptions.indels.get
    this.consensusDeterminationModel = cleanModelEnum
    this.noPGTag = testMode;
    this.scatterCount = gatkOptions.scatterGatherCount.get
    this.analysisName = projId + "_clean"
    this.jobName = projId + "_clean"
  }

  case class cov(inBam: File, outRecalFile: File, defaultPlatform: String) extends BaseRecalibrator with CommandLineGATKArgs {

    this.num_cpu_threads_per_data_thread = gatkOptions.nbrOfThreads

    if (!gatkOptions.dbSNP.isEmpty)
      this.knownSites ++= gatkOptions.dbSNP.get
    this.covariate ++= Seq("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "ContextCovariate")
    this.input_file :+= inBam
    this.disable_indel_quals = false
    this.out = outRecalFile
    if (!defaultPlatform.isEmpty) this.default_platform = defaultPlatform
    if (!gatkOptions.intervalFile.isEmpty) this.intervals :+= gatkOptions.intervalFile.get

    this.scatterCount = gatkOptions.scatterGatherCount.get
    this.analysisName = projId + "_cov"
    this.jobName = projId + "_cov"
  }

  case class recal(inBam: File, inRecalFile: File, outBam: File) extends PrintReads with CommandLineGATKArgs {

    //TODO This should probably be a core job since it does not support parallel exection.   

    this.input_file :+= inBam

    this.BQSR = inRecalFile
    this.baq = CalculationMode.CALCULATE_AS_NECESSARY
    this.out = outBam
    this.scatterCount = gatkOptions.scatterGatherCount.get
    this.num_cpu_threads_per_data_thread = gatkOptions.nbrOfThreads
    this.isIntermediate = false
    this.analysisName = projId + "_recal"
    this.jobName = projId + "_recal"
  }

}