package molmed.qscripts.legacy

import org.broadinstitute.sting.queue.QScript
import scala.xml._
import collection.JavaConversions._
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.util.QScriptUtils
import molmed.queue.setup._
import molmed.utils.GeneralUtils
import molmed.utils.UppmaxXMLConfiguration

/**
 * This is just a ugly hack I use onced to merge bam file on
 * their file names. (And that's also what it does...)
 */
class MergeBamFilesOnFileName extends QScript with UppmaxXMLConfiguration {
    qscript =>

    @Input(doc = "List of bam files to merge", fullName = "input", shortName = "i", required = true)
    var input: File = _

    @Argument(doc="Output path for the processed BAM files.", fullName="output_directory", shortName="outputDir", required=true)
    var outputDir: String = _
    
    /**
     * **************************************************************************
     * Main script
     * **************************************************************************
     */

    def script() {

        // Get all the input files form the list
        val files: Seq[File] = QScriptUtils.createSeqFromFile(input)
        
        val uppmaxConfig = loadUppmaxConfigFromXML()
        val generalUtils = new GeneralUtils(projectName, uppmaxConfig)

        // Create Map of all the input files with the file names as keys
        val fileMap = createSampleFileMap(files)

        // For each key in the map create a new output file to write to
        for (fileName <- fileMap.keys) {

            val mergedBam = new File(outputDir + "/" + fileName)                        

            // Get the Seq of files corresponding to the file name and merge all those files.
            add(generalUtils.joinBams(fileMap(fileName), mergedBam))
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
}