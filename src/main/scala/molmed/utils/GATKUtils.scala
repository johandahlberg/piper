package molmed.utils

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk.CommandLineGATK
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.utils.baq.BAQ.CalculationMode
import org.broadinstitute.sting.gatk.walkers.indels.IndelRealigner.ConsensusDeterminationModel
import org.broadinstitute.sting.commandline.Argument

class GATKUtils(gatkOptions: GATKOptions, projectName: Option[String], projId: String, uppmaxQoSFlag: Option[String]) extends UppmaxUtils(projId, uppmaxQoSFlag) {

  // General arguments to GATK walkers
  trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {
    this.reference_sequence = gatkOptions.reference
  }

  case class DepthOfCoverage(inBam: File, outputDir: File) extends org.broadinstitute.sting.queue.extensions.gatk.DepthOfCoverage with CommandLineGATKArgs with NineGbRamJobs {
    this.input_file = Seq(inBam)
    this.out = outputDir
    if (!gatkOptions.intervalFile.isEmpty) this.intervals :+= gatkOptions.intervalFile.get
    this.isIntermediate = false
    this.analysisName = projectName.get + "_depth_of_coverage"
    this.jobName = projectName.get + "_depth_of_coverage"
    this.omitBaseOutput = true
  }

  case class target(inBams: Seq[File], outIntervals: File, @Argument cleanModelEnum: ConsensusDeterminationModel) extends RealignerTargetCreator with CommandLineGATKArgs {

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
    this.analysisName = projectName.get + "_targets"
    this.jobName = projectName.get + "_targets"
  }

  case class clean(inBams: Seq[File], tIntervals: File, outBam: File, @Argument cleanModelEnum: ConsensusDeterminationModel, testMode: Boolean) extends IndelRealigner with CommandLineGATKArgs {

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
    this.analysisName = projectName.get + "_clean"
    this.jobName = projectName.get + "_clean"
  }

  case class cov(inBam: File, outRecalFile: File, @Argument defaultPlatform: String) extends BaseRecalibrator with CommandLineGATKArgs {

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
    this.analysisName = projectName.get + "_cov"
    this.jobName = projectName.get + "_cov"
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
    this.analysisName = projectName.get + "_recal"
    this.jobName = projectName.get + "_recal"
  }

}