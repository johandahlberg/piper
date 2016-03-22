package molmed.queue.extensions.picard

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

import org.broadinstitute.gatk.utils.commandline._
import java.io.File
import org.broadinstitute.gatk.queue.extensions.picard.PicardBamFunction
import org.broadinstitute.gatk.queue.function.JavaCommandLineFunction

import molmed.utils.GeneralUtils


/**
 * This MarkDuplicates extention is identical to the one provided with GATK
 * Queue with the exception that the annotations for the output, outputIndex and
 * metrics files are left to subclasses extending this wrapper.
 */
trait MarkDuplicatesWrapper extends JavaCommandLineFunction with PicardBamFunction {
  analysisName = "MarkDuplicates"
  javaMainClass = "picard.sam.MarkDuplicates"

  var output: File
  var metrics: File

  @Input(doc="The input SAM or BAM files to analyze.  Must be coordinate sorted.", shortName = "input", fullName = "input_bam_files", required = true)
  var input: Seq[File] = Nil

  @Argument(doc="If true do not write duplicates to the output file instead of writing them with appropriate flags set.", shortName = "remdup", fullName = "remove_duplicates", required = false)
  var REMOVE_DUPLICATES: Boolean = false

  @Argument(doc = "Maximum number of file handles to keep open when spilling read ends to disk.  Set this number a little lower than the per-process maximum number of file that may be open.  This number can be found by executing the 'ulimit -n' command on a Unix system.", shortName = "max_file_handles", fullName ="max_file_handles_for_read_ends_maps", required=false)
  var MAX_FILE_HANDLES_FOR_READ_ENDS_MAP: Int = -1;

  @Argument(doc = "This number, plus the maximum RAM available to the JVM, determine the memory footprint used by some of the sorting collections.  If you are running out of memory, try reducing this number.", shortName = "sorting_ratio", fullName = "sorting_collection_size_ratio", required = false)
  var SORTING_COLLECTION_SIZE_RATIO: Double = -1

  override def inputBams = input
  override def outputBam = output
  this.sortOrder = null
  this.createIndex = Some(true)
  override def commandLine = super.commandLine +
    required("M=" + metrics) +
    conditional(REMOVE_DUPLICATES, "REMOVE_DUPLICATES=true") +
    conditional(MAX_FILE_HANDLES_FOR_READ_ENDS_MAP > 0, "MAX_FILE_HANDLES_FOR_READ_ENDS_MAP=" + MAX_FILE_HANDLES_FOR_READ_ENDS_MAP.toString) +
    conditional(SORTING_COLLECTION_SIZE_RATIO > 0, "SORTING_COLLECTION_SIZE_RATIO=" + SORTING_COLLECTION_SIZE_RATIO.toString)
}

/**
 * This MarkDuplicates extention extends the wrapper above and annotates the metrics file as an argument
 * instead of as an output. This means it will not be removed if there the
 * class is added as an intermediate step in your pipeline. Otherwise, the functionality is identical
 * to the MarkDuplicates extension supplied with GATK.
 */
class MarkDuplicates extends MarkDuplicatesWrapper {

  @Output(doc="The output file to write marked records to", shortName = "output", fullName = "output_bam_file", required = true)
  var output: File = _

  @Output(doc="The output bam index", shortName = "out_index", fullName = "output_bam_index_file", required = false)
  var outputIndex: File = _

  @Argument(doc="File to write duplication metrics to", shortName = "out_metrics", fullName = "output_metrics_file", required = true)
  var metrics: File = new File(output + ".metrics")

  override def freezeFieldValues() {
    super.freezeFieldValues()
    if (outputIndex == null && output != null)
      outputIndex = new File(output.getName.stripSuffix(".bam") + ".bai")
  }
}

/**
 * This MarkDuplicates extention extends the wrapper above and discards the output and outputIndex,
 * just generating and keeping the metrics file (as an output). NB: this behaviour is Unix-specific.
 */
class MarkDuplicatesMetrics extends MarkDuplicatesWrapper {

  var output: File = new File("/dev/null")

  @Output(doc="File to write duplication metrics to", shortName = "out_metrics", fullName = "output_metrics_file", required = true)
  var metrics: File = _

  this.createIndex = Some(false)

}