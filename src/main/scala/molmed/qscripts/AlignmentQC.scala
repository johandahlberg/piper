package org.broadinstitute.sting.queue.qscripts

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.commandline
import collection.JavaConversions._
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.util.QScriptUtils

class AlignmentQC extends QScript {
    qscript =>

    /**
     * **************************************************************************
     * Required Parameters
     * **************************************************************************
     */

    @Input(doc = "input BAM file - or list of BAM files to QC", fullName = "input", shortName = "i", required = true)
    var input: File = _

    @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = true)
    var reference: File = _

    /**
     * **************************************************************************
     * Optional Parameters
     * **************************************************************************
     */
    
    @Argument(doc = "UPPMAX project id", fullName = "project_id", shortName = "pid", required = false)
    var projId: String = _

    @Input(doc = "an intervals file to be used by GATK - output bams at intervals only", fullName = "gatk_interval_file", shortName = "intervals", required = false)
    var intervals: File = _

    @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
    var outputDir: String = ""

    /**
     * **************************************************************************
     * Main script
     * **************************************************************************
     */

    def script() {

        // Get the bam files to analyze
        val bams = QScriptUtils.createSeqFromFile(input)

        // Run QC for each of them and output to a separate dir for each sample.
        for (bam <- bams) {
            val outDir = createOutputDir(bam)
            add(depthOfCoverage(bam, outDir))
        }
    }

    def createOutputDir(file: File) = {
        val outDir = {
            val basename = file.getName().replace(".bam", "")
            if (outputDir == "") new File(basename + "/" + basename) else new File(outputDir + "/" + basename + "/" + basename)
        }
        outDir.mkdirs()
        outDir
    }

    /**
     * **************************************************************************
     * Extension classes
     * **************************************************************************
     */

    //java -jar gatk/dist/GenomeAnalysisTK.jar -T DepthOfCoverage

    // General arguments to non-GATK tools
    trait ExternalCommonArgs extends CommandLineFunction {
        this.memoryLimit = 3
        this.isIntermediate = true
        this.jobNativeArgs +:= "-p core -A " + projId
    }

    // General arguments to GATK walkers
    trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {
        this.reference_sequence = qscript.reference
    }

    case class depthOfCoverage(inBam: File, outputDir: File) extends DepthOfCoverage with CommandLineGATKArgs {
        this.input_file = Seq(inBam)
        this.out = outputDir
        this.isIntermediate = false
        this.analysisName = "DepthOfCoverage"
        this.jobName = "DepthOfCoverage"
        this.omitBaseOutput = true // TODO Add this as parameter to script
    }

}