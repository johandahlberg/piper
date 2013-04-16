package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.commandline
import collection.JavaConversions._
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.util.QScriptUtils

class RNAQC extends QScript {
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

    @Argument(doc = "Output path for the QC results", fullName = "output_directory", shortName = "outputDir", required = false)
    var outputDir: String = ""

    /**
     * **************************************************************************
     * Main script
     * **************************************************************************
     */

    def script() {

        // Get the bam files to analyze
        val bams = QScriptUtils.createSeqFromFile(input)

        // Create output dir if it does not exist
        val outDir = if (outputDir == "") new File("RNA_QC") else new File(outputDir)
        outDir.mkdirs()

        // TODO Create the input input file in specified format
        // TODO Run RNA-QC with the input file as specified.

    }

    /**
     * **************************************************************************
     * Extension classes
     * **************************************************************************
     */

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

    case class RNA_QC(inBam: File, outDir: File) extends org.broadinstitute.cga.rnaseq.RNASeqMetrics with CommandLineGATKArgs {
        this.input_file = Seq(inBam)
        this.outputDir = outDir        
        this.isIntermediate = false
        this.analysisName = "RNA_QC"
        this.jobName = "RNA_QC"        
    }

}