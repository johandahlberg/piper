package molmed.utils

import org.broadinstitute.sting.queue.function.CommandLineFunction

class UppmaxUtils(uppmaxConfig: UppmaxConfig) { 

  val clusterString = if(!uppmaxConfig.clusterName.isEmpty) " --cluster=" + uppmaxConfig.clusterName.get else ""  
  val qosFlag = if (!uppmaxConfig.uppmaxQoSFlag.isEmpty) " --qos=" + uppmaxConfig.uppmaxQoSFlag.get else ""
  val projectBaseString = " -A " + uppmaxConfig.projId + " "  + qosFlag + clusterString

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {
    this.jobNativeArgs +:= "-p core -n 8 " + projectBaseString
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