package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import scala.xml._
import collection.JavaConversions._
import net.sf.samtools.SAMFileReader
import net.sf.samtools.SAMFileHeader.SortOrder
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.commandline.Hidden
import molmed.queue.setup._
import org.broadinstitute.sting.queue.function.InProcessFunction
import org.broadinstitute.sting.utils.io.IOUtils

class MergeBamFilesOnFileName extends QScript {
    qscript =>

    @Input(doc = "List of bam files to merge", fullName = "input", shortName = "i", required = true)
    var input: File = _

    @Argument(doc="Output path for the processed BAM files.", fullName="output_directory", shortName="outputDir", required=true)
    var outputDir: String = _
    
    @Argument(doc = "Uppmax Project ID", fullName = "project", shortName = "p", required = true)
    var projId: String = _

    /**
     * **************************************************************************
     * Main script
     * **************************************************************************
     */

    def script() {

        // Get all the input files form the list
        val files: Seq[File] = QScriptUtils.createSeqFromFile(input)

        // Create Map of all the input files with the file names as keys
        val fileMap = createSampleFileMap(files)

        // For each key in the map create a new output file to write to
        for (fileName <- fileMap.keys) {

            val mergedBam = new File(outputDir + "/" + fileName)                        

            // Get the Seq of files corresponding to the file name and merge all those files.
            add(joinBams(fileMap(fileName), mergedBam))
        }

    }

    /**
     * **************************************************************************
     * Helper Methods
     * **************************************************************************
     */

    private def createSampleFileMap(files: Seq[File]): Map[String, Seq[File]] = {
        val fileMap = scala.collection.mutable.Map.empty[String, Seq[File]]
        files.foreach(file => {
            val fileName = file.getName()
            if (fileMap.contains(fileName))
                fileMap(fileName) = fileMap(fileName) ++ Seq(file)
            else
                fileMap(fileName) = Seq(file)

        })
        fileMap.toMap
    }

    /**
     * **************************************************************************
     * Case classes - used by qgrapth to setup the job run order.
     * **************************************************************************
     */

    // General arguments to non-GATK tools
    trait ExternalCommonArgs extends CommandLineFunction {

        this.jobNativeArgs +:= "-p node -A " + projId
        this.memoryLimit = 24
        this.isIntermediate = false
    }

    case class joinBams(inBams: Seq[File], outBam: File) extends MergeSamFiles with ExternalCommonArgs {
        this.input = inBams
        this.output = outBam
        this.createIndex = true

        this.analysisName = "joinBams"
        this.jobName = "joinBams"
    }
}