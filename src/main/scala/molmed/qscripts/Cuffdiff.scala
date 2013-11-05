package molmed.qscripts
import collection.JavaConversions._
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.queue.util.QScriptUtils
import java.io.File
import net.sf.samtools.SAMFileReader
import molmed.utils.ReadGroupUtils
import molmed.utils.Uppmaxable
import molmed.utils.UppmaxUtils

class Cuffdiff extends QScript with Uppmaxable {

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

  @Input(doc = "The path to the binary of cufflinks", fullName = "path_to_cuffdiff", shortName = "cuffdiff", required = true)
  var cuffdiffPath: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Argument(doc = "File specifing if there are any replicates in the cohort. On each line should be the label (e.g. the name of the condition) and sample names of the samples included in that condition seperated by tabs." +
    "Please note that only samples which have replicates need to be specified. The default is one sample - one replicate", fullName = "replicates", shortName = "rep", required = false)
  var replicatesFile: Option[File] = None

  @Argument(doc = "Output path for the processed files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""
  def getOutputDir: String = if (outputDir.isEmpty()) "" else outputDir + "/"

  @Argument(doc = "Number of threads to use", fullName = "threads", shortName = "nt", required = false)
  var threads: Int = 1

  @Argument(doc = "library type. Options: fr-unstranded (default), fr-firststrand, fr-secondstrand", fullName = "library_type", shortName = "lib", required = false)
  var libraryType: String = "fr-unstranded"

  @Argument(doc = "Annotations of known transcripts in GTF 2.2 or GFF 3 format.", fullName = "annotations", shortName = "a", required = false)
  var annotations: Option[File] = None

  /**
   *  Help methods
   */

  def getReplicatesFromFile(file: File): Map[String, List[String]] = {
    val lines = scala.io.Source.fromFile(file).getLines
    val conditionSampleTuples = for (line <- lines) yield {
      val values = line.split("\t")
      require(values.size > 2, "Could not find any replicates for all lines in: " + file.getAbsolutePath())
      val conditionName = values(0)
      val sampleNames = values.drop(1).toList
      (conditionName, sampleNames)
    }
    conditionSampleTuples.toMap
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
    val replicates: Map[String, List[String]] = if (replicatesFile.isDefined) getReplicatesFromFile(replicatesFile.get) else Map.empty

    val samplesAndLables = bams.map(file => (file, ReadGroupUtils.getSampleNameFromReadGroups(file))).toMap

    val cuffDiffUtils = new CuffDiffUtils
    val placeHolderFile = new File(getOutputDir + "qscript_cufflinks.stdout.log")
    add(cuffDiffUtils.cuffdiff(samplesAndLables, replicates, placeHolderFile))

  }

  class CuffDiffUtils extends UppmaxUtils(projectName, projId, uppmaxQoSFlag) {
    case class cuffdiff(samplesAndLables: Map[File, String], replicates: Map[String, List[String]], outputFile: File) extends FatNode {

      this.isIntermediate = false

      @Input var bamFiles: Seq[File] = samplesAndLables.keys.toSeq
      @Argument var labels: String = samplesAndLables.map(f => f._2).mkString(",")
      @Output var stdOut: File = outputFile

      /**
       * This function will merge all samples with identical names into the same condition
       * and check the if there are further replications to handle from the replication file.
       */
      def mapFilesToConditions(): Map[String, Seq[File]] = {

        def mergeIdenticalSamplesToReplicates(): Map[String, Seq[File]] = {
          samplesAndLables.foldLeft(Map.empty[String, Seq[File]])((map, tupple) => {
            val sampleName = tupple._2
            val file = tupple._1

            if (map.contains(sampleName))
              map.updated(sampleName, map(sampleName) :+ file)
            else
              map.updated(sampleName, Seq(file))
          })
        }

        if (replicates.isEmpty)
          mergeIdenticalSamplesToReplicates()
        else {

          val identicalSamplesToFileMap = mergeIdenticalSamplesToReplicates()
          val conditionsAndFiles = for (
            (condition, sampleNames) <- replicates
          ) yield {
            val samplesAndFileFoundInReplicateFile = identicalSamplesToFileMap.filterKeys(sampleName => sampleNames.contains(sampleName))
            (condition, samplesAndFileFoundInReplicateFile.values.flatten.toSeq)
          }

          val samplesAndfilesNotInRelicatesFile = identicalSamplesToFileMap.
            filterNot(f =>
              { replicates.values.flatten.contains(f._1) })

          conditionsAndFiles ++ samplesAndfilesNotInRelicatesFile
        }
      }

      val conditionsAndFiles = mapFilesToConditions
      val labelsString: String = conditionsAndFiles.keys.mkString(",")
      val inputFilesString: String = conditionsAndFiles.values.map(fileList => fileList.mkString(",")).mkString(" ")

      require(!labelsString.isEmpty(), "Lables string in empty. Something went wrong!")
      require(!inputFilesString.isEmpty(), "Input file string in empty. Something went wrong!")

      def commandLine = cuffdiffPath + "/cuffdiff" +
        " --library-type " + libraryType + " " +
        " -p " + threads +
        (if (!getOutputDir.isEmpty) " -o " + getOutputDir + " " else "") +
        " --labels " + labelsString + " " +
        annotations.get.getAbsolutePath() + " " +
        inputFilesString +
        " 1> " + stdOut

      this.analysisName = "cuffdiff"
      this.jobName = "cuffdiff"
    }
  }

}