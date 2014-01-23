package molmed.utils

import org.broadinstitute.sting.queue.function.CommandLineFunction

/**
 * Utility class holding uppmax resource configurations and settings. 
 * Extend this to be able to extend your commandline wrappers with the
 * the resource management traits, e.g. TwoCores.
 */
class UppmaxJob(uppmaxConfig: UppmaxConfig) {

  val qosFlag = if (!uppmaxConfig.uppmaxQoSFlag.isEmpty) " --qos=" + uppmaxConfig.uppmaxQoSFlag.get else ""
  val projectBaseString = Seq("-A " + uppmaxConfig.projId, qosFlag)

  /**
   * Make sure that the memory limit does not get set higher than 10 if testmode is activated,
   * otherwise some jobs will go bananas on the local test machine, causing it to swap.
   */
  def memLimit(memLimit: Option[Double]): Option[Double] = if (uppmaxConfig.testMode) Some(10) else memLimit

  trait OneCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 1") ++ projectBaseString
    this.memoryLimit = memLimit(Some(8))
    this.isIntermediate = false
  }

  trait TwoCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 2") ++ projectBaseString
    this.memoryLimit = memLimit(Some(16))
    this.isIntermediate = false
  }

  trait ThreeCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 3") ++ projectBaseString
    this.memoryLimit = memLimit(Some(24))
    this.isIntermediate = false
  }

  trait SixCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 6") ++ projectBaseString
    this.memoryLimit = memLimit(Some(48))
    this.isIntermediate = false
  }

  trait EightCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p core -n 8") ++ projectBaseString
    this.memoryLimit = memLimit(Some(64))
    this.isIntermediate = false
  }

  trait SixteenCoreJob extends CommandLineFunction {
    this.jobNativeArgs = Seq("-p node") ++ projectBaseString
    this.memoryLimit = memLimit(Some(128))
    this.isIntermediate = false
  }

}