package molmed.qscripts
import collection.JavaConversions._

import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.queue.util.QScriptUtils
import java.io.File

class Cufflinks extends QScript {

    qscript =>

    /**
     * **************************************************************************
     * Required Parameters
     * **************************************************************************
     */

    // TODO Write better input
    @Input(doc = "input", fullName = "input", shortName = "i", required = true)
    var input: File = _

    /**
     * **************************************************************************
     * Optional Parameters
     * **************************************************************************
     */

    @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = false)
    var reference: File = _

    @Input(doc = "The path to the binary of cufflinks", fullName = "path_to_cufflinks", shortName = "cufflinks", required = false)
    var cufflinksPath: File = _

    @Argument(doc = "Output path for the processedfiles.", fullName = "output_directory", shortName = "outputDir", required = false)
    var outputDir: String = ""

    @Argument(doc = "Number of threads to use", fullName = "threads", shortName = "nt", required = false)
    var threads: Int = 1

    //TODO Add cufflinks specific stuff

    /**
     * **************************************************************************
     * Private variables
     * **************************************************************************
     */

    var projId: String = ""

    def createOutputDir(file: File) = {
        val outDir = {
            val basename = file.getName().replace(".bam", "")
            if (outputDir == "") {
                new File("cufflinks/" + basename)
            } else {
                new File(outputDir + "/cufflinks/" + basename)
            }
        }
        outDir.mkdirs()
        outDir
    }

    /**
     * The actual script
     */
    def script {

        // final output lists
        var cohortList: Seq[File] = Seq()
        var placeHolderList: Seq[File] = Seq()
        var outputDirList: Seq[File] = Seq()

        val bams = QScriptUtils.createSeqFromFile(input)

        for (bam <- bams) {
            val outDir = createOutputDir(bam)
            val placeHolderFile = File.createTempFile("temporaryLogFile", ".txt")

            add(cufflinks(bam, outDir, placeHolderFile))
            placeHolderList :+= placeHolderFile
            outputDirList :+= outDir
        }

        val transcriptList = new File(qscript.outputDir + "cufflinks_transcript.cohort.list")
        add(writeTranscriptList(transcriptList, outputDirList, placeHolderList))

        // Then cuffmerge -s /seqdata/fastafiles/hg19/hg19.fa assemblies.txt
        // assemblies: File, outputDir: File, reference: File, outputFile: File
        val placeHolderFile = File.createTempFile("temporaryLogFile", ".txt")
        add(cuffmerge(transcriptList, outputDir + "/cuffmerge/", reference, placeHolderFile))
    }

    // General arguments to non-GATK tools
    trait ExternalCommonArgs extends CommandLineFunction {

        this.jobNativeArgs +:= "-p node -A " + projId
        this.memoryLimit = 24
        this.isIntermediate = false
    }

    case class writeTranscriptList(transcriptList: File, outputDirList: Seq[File], placeHolder: Seq[File]) extends ListWriterFunction {

        @Input val ph = placeHolder
        this.listFile = transcriptList
        this.inputFiles = outputDirList.map(file => { file.getAbsolutePath() + "/transcripts.gtf" })
        this.analysisName = "writeTranscriptList"
        this.jobName = "writeTranscriptList"

    }

    case class cufflinks(inputBamFile: File, sampleOutputDir: File, outputFile: File) extends CommandLineFunction with ExternalCommonArgs {

        // Sometime this should be kept, sometimes it shouldn't
        this.isIntermediate = false

        @Input var bamFile = inputBamFile
        @Input var dir = sampleOutputDir
        @Output var stdOut = outputFile

        //cufflinks -o cufflinks_brain tophat_brain/accepted_hits.bam        
        def commandLine = "cufflinks -p " + threads + " -o " + sampleOutputDir + " " + bamFile +
            " 1> " + stdOut
    }

    case class cuffmerge(assemblies: File, outputDir: File, reference: File, outputFile: File) extends CommandLineFunction with ExternalCommonArgs {

        // Sometime this should be kept, sometimes it shouldn't
        this.isIntermediate = false

        @Input var as = assemblies
        @Input var dir = outputDir
        @Input var ref = reference
        @Output var stdOut = outputFile

        //cuffmerge -s /seqdata/fastafiles/hg19/hg19.fa assemblies.txt
        def commandLine = "cuffmerge -p " + threads + " -o " + dir + " -s " + ref + " " + assemblies +
            " 1> " + stdOut

    }
}