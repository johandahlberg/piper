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

    @Argument(doc = "Perform downsampling to the given number of reads.", shortName = "d", fullName = "downsample", required = false)
    var downsample: Int = -1

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
            add(RNA_QC(bam, reference, outDir, transcripts, rRNATargets))
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

    case class RNA_QC(@Input bamfile: File, referenceFile: File, outDir: File, transcriptFile: File, rRNATargetsFile: File) extends RNASeQC with ExternalCommonArgs {

        def createRNASeQCInputString(file: File): String = {
            import molmed.utils.BamUtils._
            val sampleName = getSampleNameFromReadGroups(file)
            sampleName + "|" + file.getAbsolutePath() + "|" + sampleName
        }

        val inputString = createRNASeQCInputString(bamfile)
        
        this.input = inputString
        this.output = outDir
        this.reference = referenceFile
        this.transcripts = transcriptFile
        this.rRNATargets = rRNATargetsFile
        this.downsample = qscript.downsample

        this.isIntermediate = false
        this.analysisName = "RNA_QC"
        this.jobName = "RNA_QC"
    }
}