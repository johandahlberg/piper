package molmed.qscripts

import org.broadinstitute.gatk.queue.QScript
import java.io.File

class TestConcurrencyMaxScript extends QScript {

  // Where you define the pipeline
  def script() {

	  for(i <- 1 to 10)
		  this.add(Sleep())

  }

  case class Sleep() extends CommandLineFunction {
    // Another way to define the commandline
    def commandLine = """sleep 10; echo "hi on stderr" > /dev/stderr;"""
  }
}