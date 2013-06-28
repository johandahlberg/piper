package molmed.qscripts
import collection.JavaConversions._
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.queue.util.QScriptUtils
import java.io.File
import net.sf.samtools.SAMFileReader

class Cuffdiff extends QScript {

    qscript =>

    /**
     * **************************************************************************
     * Required Parameters
     * **************************************************************************
     */

    @Input(doc = "input cohort file. One bam file per line.", fullName = "input", shortName = "i", required = true)
    var input: File = _

    @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = true)
    var reference: File = _

    @Input(doc = "The path to the binary of cufflinks", fullName = "path_to_cufflinks", shortName = "cufflinks", required = true)
    var cuffdiffPath: File = _

    /**
     * **************************************************************************
     * Optional Parameters
     * **************************************************************************
     */

    @Argument(doc = "Output path for the processed files.", fullName = "output_directory", shortName = "outputDir", required = false)
    var outputDir: String = ""

    @Argument(doc = "Number of threads to use", fullName = "threads", shortName = "nt", required = false)
    var threads: Int = 1

    @Argument(doc = "library type. Options: fr-unstranded (default), fr-firststrand, fr-secondstrand", fullName = "library_type", shortName = "lib", required = false)
    var libraryType: String = "fr-unstranded"

    @Argument(doc = "Annotations of known transcripts in GTF 2.2 or GFF 3 format.", fullName = "annotations", shortName = "a", required = false)
    var annotations: Option[File] = None

    /**
     * **************************************************************************
     * Private variables
     * **************************************************************************
     */

    var projId: String = ""

    /**
     *  Help methods
     */
    def getSampleNameFromReadGroups(bam: File): String = {
    		val samFileReader = new SAMFileReader(bam)
    		val samHeader = samFileReader.getFileHeader()
    		val sampleNames = samHeader.getReadGroups().map(rg => rg.getSample())
    		require(!sampleNames.isEmpty, "Couldn't find read groups in file: " + bam.getAbsolutePath() + ". This is required for the script to work.")
    		require(sampleNames.length == 1, "More than one sample in file: " + bam.getAbsolutePath() +
    				". Please make sure that there is only one sample per file in input.")
    		sampleNames(0)		
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

        val samplesAndLables = bams.map(file => (file, getSampleNameFromReadGroups(file)))

        val placeHolderFile = new File(outputDir + "/qscript_cufflinks.stdout.log")
        add(cuffdiff(samplesAndLables, placeHolderFile))

    }

    // General arguments to non-GATK tools
    trait ExternalCommonArgs extends CommandLineFunction {

        this.jobNativeArgs +:= "-p node -A " + projId
        this.memoryLimit = 24
        this.isIntermediate = false
    }

    case class cuffdiff(samplesAndLables: Seq[(File, String)], outputFile: File) extends CommandLineFunction with ExternalCommonArgs {

        // Sometime this should be kept, sometimes it shouldn't
        this.isIntermediate = false

        //@TODO Handle replicates

        @Input var bamFiles = samplesAndLables.map(f => f._1).mkString(" ")
        @Input var lables = samplesAndLables.map(f => f._2).mkString(",")
        @Output var stdOut = outputFile

        def commandLine = cuffdiffPath +
            " --library-type " + libraryType + " " +
            " -p " + threads +
            " -o " + outputDir + " " +
            " --lables " + lables + " "
        annotations.get.getAbsolutePath() + " "
        bamFiles +
            " 1> " + stdOut

        this.analysisName = "cuffdiff"
        this.jobName = "cuffdiff"
    }
}