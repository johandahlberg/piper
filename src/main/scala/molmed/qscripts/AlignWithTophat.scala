package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import scala.xml._
import collection.JavaConversions._
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.function.ListWriterFunction
import molmed.queue.setup._
import java.io.File
import java.io.PrintWriter

class AlignWithTophat extends QScript {

    qscript =>

    /**
     * **************************************************************************
     * Required Parameters
     * **************************************************************************
     */

    @Input(doc = "input pipeline setup xml", fullName = "input", shortName = "i", required = true)
    var input: File = _

    /**
     * **************************************************************************
     * Optional Parameters
     * **************************************************************************
     */

    @Input(doc = "The path to the binary of tophat", fullName = "path_to_tophat", shortName = "tophat", required = false)
    var tophatPath: File = _

    @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
    var samtoolsPath: File = "/usr/bin/samtools"

    @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
    var outputDir: String = ""

    @Argument(doc = "Perform validation on the BAM files", fullName = "validation", shortName = "vs", required = false)
    var validation: Boolean = false

    @Argument(doc = "Number of threads tophat should use", fullName = "tophat_threads", shortName = "tt", required = false)
    var tophatThreads: Int = 1

    @Argument(doc = "library type. Options: fr-unstranded (default), fr-firststrand, fr-secondstrand", fullName = "library_type", shortName = "lib", required = false)
    var libraryType: String = "fr-unstranded"

    @Argument(doc = "Annotations of known transcripts in GTF 2.2 or GFF 3 format.", fullName = "annotations", shortName = "a", required = false)
    var annotations: Option[File] = None

    @Argument(doc = "Do fussion search using tophat", fullName = "fusionSearch", shortName = "fs", required = false)
    var fusionSearch: Boolean = false

    //TODO Add tophat specific stuff

    /**
     * **************************************************************************
     * Private variables
     * **************************************************************************
     */

    var projId: String = ""

    /**
     * Help methods
     */

    def performAlignment(sampleName: String, fastqs: ReadPairContainer, reference: File, readGroupInfo: String): (File, File) = {

        // All fastqs input to this function should be from the same sample
        // and should all be aligned to the same reference.
        val sampleDir = new File(outputDir + sampleName)
        sampleDir.mkdirs()
        var alignedBamFile: File = new File(sampleDir + "/" + "accepted_hits.bam")

        val placeHolderFile = new File(sampleDir + "/qscript_tophap.stdout.log")

        add(tophat(fastqs.mate1, fastqs.mate2, sampleDir, reference, placeHolderFile, readGroupInfo))

        return (alignedBamFile, placeHolderFile)
    }

    private def alignSamples(sampleMap: Map[String, Seq[SampleAPI]]): (Seq[File], Seq[File]) = {

        var cohortSeq: Seq[File] = Seq()
        var placeHolderSeq: Seq[File] = Seq()

        /**
         * Make sure that if there are several instances of a sample
         * they are aligned separately with folder names: 
         * <original sample name>_<int>
         */
        for ((sampleName, samples) <- sampleMap) {
            if (samples.size == 1) {
                val (file, placeholder) = performAlignment(sampleName, samples(0).getFastqs, samples(0).getReference, samples(0).getTophatStyleReadGroupInformationString)
                cohortSeq :+= file
                placeHolderSeq :+= placeholder
            } else {
                var counter = 1
                for (sample <- samples) {
                    val (file, placeholder) = performAlignment(sampleName + "_" + counter, sample.getFastqs, sample.getReference, sample.getTophatStyleReadGroupInformationString)
                    counter += 1
                    cohortSeq :+= file
                    placeHolderSeq :+= placeholder
                }
            }
        }
        return (cohortSeq, placeHolderSeq)
    }

    /**
     * The actual script
     */
    def script {

        val setupReader: SetupXMLReader = new SetupXMLReader(input)
        val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
        projId = setupReader.getUppmaxProjectId()

        val (cohortList: Seq[File], placeHolderList: Seq[File]) = alignSamples(samples)

        // output a BAM list with all the processed files
        val cohortFile = new File(qscript.outputDir + setupReader.getProjectName() + ".cohort.list")
        add(writeList(cohortList, cohortFile, placeHolderList))

    }

    /**
     * Case classes for running command lines
     */

    // General arguments to non-GATK tools
    trait ExternalCommonArgs extends CommandLineFunction {

        this.jobNativeArgs +:= "-p node -A " + projId
        this.memoryLimit = 24
        this.isIntermediate = false
    }

    case class writeList(inBams: Seq[File], outBamList: File, placeHolder: Seq[File]) extends ListWriterFunction {

        @Input
        val ph = placeHolder

        this.inputFiles = inBams
        this.listFile = outBamList
        this.analysisName = "bamList"
        this.jobName = "bamList"
    }

    case class tophat(fastqs1: File, fastqs2: File, sampleOutputDir: File, reference: File, outputFile: File, readGroupInfo: String) extends CommandLineFunction with ExternalCommonArgs {

        // Sometime this should be kept, sometimes it shouldn't
        this.isIntermediate = false

        @Input var files1 = fastqs1.getAbsolutePath()
        @Input var files2 = if(fastqs2 != null) fastqs2.getAbsolutePath() else ""
        @Input var dir = sampleOutputDir
        @Input var ref = reference

        @Output var stdOut = outputFile

        // Only add --GTF option if this has been defined as an option on the command line
        def annotationString = if (annotations.isDefined) " --GTF " + annotations.get.getAbsolutePath() + " " else ""

        // Only do fussion search if it has been defined on the command line.    
        def fusionSearchString = if (fusionSearch) " --fusion-search " else ""

        def commandLine = tophatPath +
            " --library-type " + libraryType +
            annotationString +
            " -p " + tophatThreads +
            " --output-dir " + dir +
            " " + readGroupInfo +
            " --keep-fasta-order " +
            fusionSearchString +
            ref + " " + files1 + " " + files2 +
            " 1> " + stdOut
    }
}
