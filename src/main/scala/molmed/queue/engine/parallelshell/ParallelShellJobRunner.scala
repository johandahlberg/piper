/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package molmed.queue.engine.parallelshell

import org.broadinstitute.gatk.queue.function.CommandLineFunction
import org.broadinstitute.gatk.queue.engine.{ RunnerStatus, CommandLineJobRunner }
import java.util.Date
import org.broadinstitute.gatk.utils.Utils
import org.broadinstitute.gatk.utils.runtime.{ ProcessSettings, OutputStreamSettings, ProcessController }
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{ Success, Failure }
import org.broadinstitute.gatk.queue.util.Logging

/**
 * Runs multiple jobs locally without blocking.
 * Use this with care as it might not be the most efficient way to run things.
 * However, for some scenarios, such as running multiple single threaded
 * programs concurrently it can be quite useful.
 * 
 * All this code is based on the normal shell runner in GATK Queue and all 
 * credits for everything except the concurrency part goes to the GATK team.
 * 
 * @author Johan Dahlberg
 * 
 * @param function Command to run.
 */
class ParallelShellJobRunner(val function: CommandLineFunction) extends CommandLineJobRunner with Logging {
  // Controller on the thread that started the job
  private var controller: ProcessController = null

  // Once the application exits this promise will be fulfilled.
  val finalExitStatus = Promise[Int]()

  /**
   * Runs the function on the local shell.
   */
  def start() {
    val commandLine = Array("sh", jobScript.getAbsolutePath)
    val stdoutSettings = new OutputStreamSettings
    val stderrSettings = new OutputStreamSettings
    val mergeError = (function.jobErrorFile == null)

    stdoutSettings.setOutputFile(function.jobOutputFile, true)
    if (function.jobErrorFile != null)
      stderrSettings.setOutputFile(function.jobErrorFile, true)

    if (logger.isDebugEnabled) {
      stdoutSettings.printStandard(true)
      stderrSettings.printStandard(true)
    }

    val processSettings = new ProcessSettings(
      commandLine, mergeError, function.commandDirectory, null,
      null, stdoutSettings, stderrSettings)

    updateJobRun(processSettings)

    getRunInfo.startTime = new Date()
    getRunInfo.exechosts = Utils.resolveHostname()
    updateStatus(RunnerStatus.RUNNING)
    controller = new ProcessController()
        
    // Run the command line process in a future.
    val exectutedFuture =
      future { controller.execAndCheck(processSettings).getExitValue }

    // Register a callback on the completion of the future, making sure that
    // the status of the job is updated accordingly. 
    exectutedFuture.onComplete { tryExitStatus =>

      tryExitStatus match {
        case Success(exitStatus) => {
          logger.debug(commandLine.mkString(" ") + " :: Got return on exit status in future: " + exitStatus)
          finalExitStatus.success(exitStatus)
          getRunInfo.doneTime = new Date()
          exitStatusUpdateJobRunnerStatus(exitStatus)
        }
        case Failure(throwable) => {
          logger.debug(
            "Failed in return from run with: " +
              throwable.getClass.getCanonicalName + " :: " +
              throwable.getMessage)
          finalExitStatus.failure(throwable)
          getRunInfo.doneTime = new Date()
          updateStatus(RunnerStatus.FAILED)
        }
      }
    }

  }

  /**
   * Possibly invoked from a shutdown thread, find and
   * stop the controller from the originating thread
   */
  def tryStop() = {
    // Assumes that after being set the job may be
    // reassigned but will not be reset back to null
    if (controller != null) {
      try {
        controller.tryDestroy()
      } catch {
        case e: Exception =>
          logger.error("Unable to kill shell job: " + function.description, e)
      }
    }
  }

  /**
   * Converts a exit status to a Runner status - i.e. DONE or FAILED.
   * @param exitStatus
   */
  def exitStatusUpdateJobRunnerStatus(exitStatus: Int): Unit = {
    exitStatus match {
      case 0 => updateStatus(RunnerStatus.DONE)
      case _ => updateStatus(RunnerStatus.FAILED)
    }
  }

  /**
   * Attempts to get the status of a job by looking at if the finalExitStatus
   * promise has completed or not.
   * @return if the jobRunner has updated it's status or not.
   */
  def updateJobStatus(): Boolean = {
    logger.debug("Trying to get the job status...")
    if (finalExitStatus.isCompleted) {
      val completedExitStatus = finalExitStatus.future.value.get.get
      logger.debug("Job completed and exit status was: " + completedExitStatus)
      exitStatusUpdateJobRunnerStatus(completedExitStatus)
      true
    } else {
      false
    }
  }

}