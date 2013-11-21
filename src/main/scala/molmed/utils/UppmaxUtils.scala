package molmed.utils

import org.broadinstitute.sting.queue.function.CommandLineFunction

class UppmaxUtils(projId: String, uppmaxQoSFlag: Option[String]) {

  val qosFlag = if (!uppmaxQoSFlag.isEmpty) " --qos=" + uppmaxQoSFlag.get else ""
  val projectBaseString = " -A " + projId + " "  + qosFlag

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {
    this.jobNativeArgs +:= "-p node " + projectBaseString
    this.memoryLimit = Some(24)
    this.isIntermediate = false
  }

  trait SixGbRamJobs extends CommandLineFunction {
    this.jobNativeArgs +:= "-p core -n 2 " + projectBaseString
    this.memoryLimit = Some(6)
    this.isIntermediate = false
  }

  trait NineGbRamJobs extends CommandLineFunction {
    this.jobNativeArgs +:= "-p core -n 3 " + projectBaseString
    this.memoryLimit = Some(9)
    this.isIntermediate = false
  }
  
  trait FatNode extends CommandLineFunction {
    this.jobNativeArgs +:= "-p node -C fat" + projectBaseString
    this.memoryLimit = Some(48)
    this.isIntermediate = false
  }

}