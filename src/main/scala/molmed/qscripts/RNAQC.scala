package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.commandline
import collection.JavaConversions._
import java.io.File
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.util.QScriptUtils
import molmed.queue.extensions.RNAQC.RNASeQC

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

    @Input(doc = "GTF File defining the transcripts (must end in .gtf)", shortName = "t", fullName = "transcripts", required = true)
    var transcripts: File = _

    /**
     * **************************************************************************
     * Optional Parameters
     * **************************************************************************
     */

    @Argument(doc = "UPPMAX project id", fullName = "project_id", shortName = "pid", required = false)
    var projId: String = _

    @Argument(doc = "Output path for the QC results", fullName = "output_directory", shortName = "outputDir", required = false)
    var outputDir: String = ""

    @Argument(doc = "intervalFile for rRNA loci (must end in .list). This is an alternative flag to the -BWArRNA flag.", shortName = "rRNA", fullName = "rRNA_targets", required = false)
    var rRNATargets: File = _

    /**
     * **************************************************************************
     * Utility methods
     * **************************************************************************
     */

    def createRNASeQCInputString(file: File): String = {
        file.getName() + "|" + file.getAbsolutePath() + "|" + file.getName()
    }

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

        for (bam <- bams) {
            val inputString = createRNASeQCInputString(bam)
            add(RNA_QC(inputString, reference, outDir, transcripts, rRNATargets))
        }
    }

    /**
     * **************************************************************************
     * Extension classes
     * **************************************************************************
     */

    // General arguments to non-GATK tools
    trait ExternalCommonArgs extends CommandLineFunction {
        this.memoryLimit = 24
        this.isIntermediate = true
        this.jobNativeArgs +:= "-p node -A " + projId
    }

    //molmed.queue.extensions.RNAQC.RNASeQC
    case class RNA_QC(inputString: String, referenceFile: File, outDir: File, transcriptFile: File, rRNATargetsFile: File) extends RNASeQC with ExternalCommonArgs {
        this.input = inputString
        this.output = outDir
        this.reference = referenceFile
        this.transcripts = transcriptFile
        this.rRNATargets = rRNATargetsFile

        this.isIntermediate = false
        this.analysisName = "RNA_QC"
        this.jobName = "RNA_QC"
    }
}