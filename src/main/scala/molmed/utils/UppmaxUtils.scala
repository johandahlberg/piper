package molmed.utils

import org.broadinstitute.sting.queue.function.CommandLineFunction

class UppmaxUtils(uppmaxConfig: UppmaxConfig) { 
  
  val qosFlag = if (!uppmaxConfig.uppmaxQoSFlag.isEmpty) " --qos=" + uppmaxConfig.uppmaxQoSFlag.get else ""
  val projectBaseString = Seq("-A " + uppmaxConfig.projId, qosFlag)

  // General arguments to non-GATK tools
  trait EightCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 8") ++ projectBaseString
    this.memoryLimit = Some(64)
    this.isIntermediate = false
  }

  trait OneCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 1") ++ projectBaseString
    this.memoryLimit = Some(8)
    this.isIntermediate = false
  }

  trait TwoCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 2") ++ projectBaseString
    this.memoryLimit = Some(16)
    this.isIntermediate = false
  }
  
  trait SixCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 6") ++ projectBaseString
    this.memoryLimit = Some(48)
    this.isIntermediate = false
  }

}