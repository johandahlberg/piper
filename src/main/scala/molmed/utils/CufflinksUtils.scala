package molmed.utils

import collection.JavaConversions._
import java.io.File
import org.broadinstitute.sting.queue.function.ListWriterFunction

class CufflinksUtils (projectName: Option[String], annotations: Option[File], libraryType: String, uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig){
  
  case class cufflinks(cufflinksPath: File, maskFile: Option[File], 
      @Input bamFile: File, sampleOutputDir: File, @Output outputFile: File, 
      findNovelTranscripts: Boolean) 
      extends CufflinksUtils(projectName,annotations,libraryType, uppmaxConfig) with TwoCoreJob {
    analysisName = "cufflinks"
    // Sometime this should be kept, sometimes it shouldn't
    this.isIntermediate = false

    /*@Input var bamFile = inputBamFile
    @Input var dir = sampleOutputDir
    @Output var stdOut = outputFile*/

    var threads: Int = 2
    
    val maskFileString = if (maskFile.isDefined && maskFile.get != null) "--mask-file " + maskFile.get.getAbsolutePath() + " " else ""

    def annotationString = if (annotations.isDefined && annotations.get != null) {
      (if (findNovelTranscripts) " --GTF-guide " else " --GTF ") + annotations.get.getAbsolutePath() + " "
    } else ""

    def commandLine = cufflinksPath +
      " --library-type " + libraryType + " " +
      maskFileString + annotationString +
      " -p " + threads +
      " -o " + sampleOutputDir + " " +
      bamFile + " "
      " 1> " + outputFile

      override def jobRunnerJobName = projectName.get + "_cufflinks"
  }
  
  case class cuffmerge(cufflinksPath: File, assemblies: File, outputDir: File, reference: File, outputFile: File) extends CufflinksUtils(projectName,annotations,libraryType, uppmaxConfig) with EightCoreJob{
    // Sometime this should be kept, sometimes it shouldn't
    analysisName = "cuffmerge"
    this.isIntermediate = false

    var threads: Int = 8
      
    @Input var as = assemblies
    @Input var dir = outputDir
    @Input var ref = reference
    @Output var stdOut = outputFile

    val referenceAnnotationString = if (annotations.isDefined && annotations.get != null)
      " --ref-gtf " + annotations.get.getAbsolutePath() + " "
    else ""

    //cuffmerge -s /seqdata/fastafiles/hg19/hg19.fa assemblies.txt
    def commandLine = cufflinksPath + "/cuffmerge -p " + threads +
      " -o " + dir +
      " --ref-sequence " + ref + " " +
      referenceAnnotationString +
      assemblies +
      " 1> " + stdOut

    //override def jobRunnerJobName = projectName.get + "_cuffmerge"
  }
  
  case class cuffdiff(threads: Int, cuffdiffPath: File, samplesAndLables: Map[File, String], replicates: Map[String, List[String]], outputFile: File, outputDir: String) extends CufflinksUtils(projectName,annotations,libraryType, uppmaxConfig) with EightCoreJob {
    def getOutputDir: String = if (outputDir.isEmpty()) "" else outputDir + "/"
    analysisName = "cuffdiff"
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
        val conditionsAndFiles = for ((condition, sampleNames) <- replicates ) yield {
          val samplesAndFileFoundInReplicateFile = identicalSamplesToFileMap.filterKeys(sampleName => sampleNames.contains(sampleName))
          (condition, samplesAndFileFoundInReplicateFile.values.flatten.toSeq)
        }

        val samplesAndfilesNotInRelicatesFile = identicalSamplesToFileMap.
          filterNot(f => { replicates.values.flatten.contains(f._1) })

          conditionsAndFiles ++ samplesAndfilesNotInRelicatesFile
      }
    }

    val conditionsAndFiles = mapFilesToConditions
    val labelsString: String = conditionsAndFiles.keys.mkString(",")
    val inputFilesString: String = conditionsAndFiles.values.map(fileList => fileList.mkString(",")).mkString(" ")

    require(!labelsString.isEmpty(), "Lables string in empty. Something went wrong!")
    require(!inputFilesString.isEmpty(), "Input file string in empty. Something went wrong!")

    def commandLine = cuffdiffPath +
      " --library-type " + libraryType + " " +
      " -p " + threads +
      (if (!getOutputDir.isEmpty) " -o " + getOutputDir + " " else "") +
      " --labels " + labelsString + " " +
      annotations.get.getAbsolutePath() + " " +
      inputFilesString +
      " 1> " + stdOut

    //override def jobRunnerJobName = projectName.get + "_cuffdiff"
  }
  
  case class writeTranscriptList(transcriptList: File, outputDirList: Seq[File], placeHolder: Seq[File]) extends ListWriterFunction {
    @Input val ph = placeHolder
    this.listFile = transcriptList
    this.inputFiles = outputDirList.map(file => {new File(file.getAbsolutePath() + "/transcripts.gtf")})
    //override def jobRunnerJobName = "writeTranscriptList"
  }
}