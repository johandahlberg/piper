package molmed.utils

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk.CommandLineGATK
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._

class GATKUtils(qscript: QScript, reference: File, intervalFile: Option[File], projId: String, getUppmaxQosFlag:Option[String]) extends UppmaxUtils(projId, getUppmaxQosFlag) {

  // General arguments to GATK walkers
  trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {
    this.reference_sequence = reference
  }

  case class DepthOfCoverage(inBam: File, outputDir: File) extends org.broadinstitute.sting.queue.extensions.gatk.DepthOfCoverage with NineGbRamJobs {
    this.input_file = Seq(inBam)
    this.out = outputDir
    if(!intervalFile.isEmpty) this.intervals :+= intervalFile.get
    this.isIntermediate = false
    this.analysisName = "DepthOfCoverage"
    this.jobName = "DepthOfCoverage"
    this.omitBaseOutput = true
  }

}