package org.broadinstitute.sting.queue.extensions.picard

import org.broadinstitute.sting.commandline._

import java.io.File

class FixMateInformation extends org.broadinstitute.sting.queue.function.JavaCommandLineFunction with PicardBamFunction {

    analysisName = "FixMateInformation"
    javaMainClass = "net.sf.picard.sam.FixMateInformation"

    @Input(doc = "The input SAM or BAM files to analyze.  Must be coordinate sorted.", shortName = "input", fullName = "input_bam_files", required = true)
    var input: Seq[File] = Nil

    @Output(doc = "The output file to write marked records to", shortName = "output", fullName = "output_bam_file", required = true)
    var output: File = _

    @Output(doc = "The output bam index", shortName = "out_index", fullName = "output_bam_index_file", required = false)
    var outputIndex: File = _

    override def freezeFieldValues() {
        super.freezeFieldValues()
        if (outputIndex == null && output != null)
            outputIndex = new File(output.getName.stripSuffix(".bam") + ".bai")
    }

    override def inputBams = input
    override def outputBam = output
    this.createIndex = Some(true)

    override def commandLine = super.commandLine        

}