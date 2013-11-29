package molmed.utils

import java.io.File
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.picard.MergeSamFiles
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.queue.extensions.picard.SortSam
import net.sf.samtools.SAMFileHeader.SortOrder
import org.broadinstitute.sting.commandline.Input
import org.broadinstitute.sting.commandline.Output
import org.broadinstitute.sting.commandline.Argument
import org.broadinstitute.sting.queue.extensions.picard.MarkDuplicates
import org.broadinstitute.sting.queue.extensions.picard.ValidateSamFile
import org.broadinstitute.sting.queue.extensions.picard.AddOrReplaceReadGroups
import molmed.queue.extensions.picard.FixMateInformation
import org.broadinstitute.sting.queue.extensions.picard.RevertSam
import org.broadinstitute.sting.queue.extensions.picard.SamToFastq
import molmed.queue.extensions.picard.BuildBamIndex
import molmed.queue.extensions.RNAQC.RNASeQC
import org.broadinstitute.sting.queue.function.InProcessFunction
import java.io.PrintWriter
import scala.io.Source

class GeneralUtils(projectName: Option[String], uppmaxConfig: UppmaxConfig) extends UppmaxUtils(uppmaxConfig) {

  case class createIndex(@Input bam: File, @Output index: File) extends BuildBamIndex with OneCoreJob {
    this.input = bam
    this.output = index
  }

  case class joinBams(inBams: Seq[File], outBam: File) extends MergeSamFiles with OneCoreJob {
    this.input = inBams
    this.output = outBam

    override def jobRunnerJobName = projectName.get + "_joinBams"

    this.isIntermediate = false
  }

  case class writeList(inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    override def jobRunnerJobName = projectName.get + "_bamList"
  }

  case class sortSam(inSam: File, outBam: File, sortOrderP: SortOrder) extends SortSam with OneCoreJob {
    this.input :+= inSam
    this.output = outBam
    this.sortOrder = sortOrderP
    override def jobRunnerJobName = projectName.get + "_sortSam"
  }

  case class cutadapt(@Input fastq: File, cutFastq: File, @Argument adaptor: String, @Argument cutadaptPath: String, @Argument syncPath: String = "resources/FixEmptyReads.pl") extends OneCoreJob {

    @Output val fastqCut: File = cutFastq
    this.isIntermediate = true
    // Run cutadapt and sync via perl script by adding N's in all empty reads.  
    def commandLine = cutadaptPath + " -a " + adaptor + " " + fastq + " | perl " + syncPath + " -o " + fastqCut
    override def jobRunnerJobName = projectName.get + "_cutadapt"
  }

  case class dedup(inBam: File, outBam: File, metricsFile: File) extends MarkDuplicates with OneCoreJob {

    this.input :+= inBam
    this.output = outBam
    this.metrics = metricsFile
    this.memoryLimit = Some(16)
    override def jobRunnerJobName = projectName.get + "_dedup"
  }

  case class validate(inBam: File, outLog: File, reference: File) extends ValidateSamFile with OneCoreJob {
    this.input :+= inBam
    this.output = outLog
    this.REFERENCE_SEQUENCE = reference
    this.isIntermediate = false
    override def jobRunnerJobName = projectName.get + "_validate"
  }

  case class fixMatePairs(inBam: Seq[File], outBam: File) extends FixMateInformation with OneCoreJob {
    this.input = inBam
    this.output = outBam
    override def jobRunnerJobName = projectName.get + "_fixMates"
  }

  case class revert(inBam: File, outBam: File, removeAlignmentInfo: Boolean) extends RevertSam with OneCoreJob {
    this.output = outBam
    this.input :+= inBam
    this.removeAlignmentInformation = removeAlignmentInfo;
    this.sortOrder = if (removeAlignmentInfo) { SortOrder.queryname } else { SortOrder.coordinate }
    override def jobRunnerJobName = projectName.get + "_revert"

  }

  case class convertToFastQ(inBam: File, outFQ: File) extends SamToFastq with OneCoreJob {
    this.input :+= inBam
    this.fastq = outFQ
    override def jobRunnerJobName = projectName.get + "_convert2fastq"
  }

  case class RNA_QC(@Input bamfile: File, @Input bamIndex: File, rRNATargetsFile: File, downsampling: Int, referenceFile: File, outDir: File, transcriptFile: File, placeHolder: File, pathRNASeQC: File) extends RNASeQC with OneCoreJob {      
    
    import molmed.utils.ReadGroupUtils._

    def createRNASeQCInputString(file: File): String = {
      val sampleName = getSampleNameFromReadGroups(file)
      "\"" + sampleName + "|" + file.getAbsolutePath() + "|" + sampleName + "\""
    }

    val inputString = createRNASeQCInputString(bamfile)

    this.input = inputString
    this.output = outDir
    this.reference = referenceFile
    this.transcripts = transcriptFile
    this.rRNATargetString = if (rRNATargetsFile != null) " -rRNA " + rRNATargetsFile.getAbsolutePath() + " " else ""
    this.downsampleString = if (downsampling > 0) " -d " + downsampling + " " else ""
    this.placeHolderFile = placeHolder
    this.pathToRNASeQC = pathRNASeQC

    this.isIntermediate = false
    override def jobRunnerJobName = projectName.get + "_RNA_QC"
  }

  case class createAggregatedMetrics(@Input placeHolderSeq: Seq[File], @Input outputDir: File, @Output aggregatedMetricsFile: File) extends InProcessFunction {

    def run() = {

      def getFileTree(f: File): Stream[File] =
        f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree)
        else Stream.empty)

      val writer = new PrintWriter(aggregatedMetricsFile)
      val metricsFiles = getFileTree(outputDir).filter(file => file.getName().matches("metrics.tsv"))
      val header = Source.fromFile(metricsFiles(0)).getLines.take(1).next.toString()

      writer.println(header)
      metricsFiles.foreach(file =>
        for (row <- Source.fromFile(file).getLines.drop(1))
          writer.println(row))

      writer.close()

    }
  }

}

object GeneralUtils {

  /**
   * Check that all the files that make up bwa index exist for the reference.
   */
  def checkReferenceIsBwaIndexed(reference: File): Unit = {
    assert(reference.exists(), "Could not find reference.")

    val referenceBasePath: String = reference.getAbsolutePath()
    for (fileEnding <- Seq("amb", "ann", "bwt", "pac", "sa")) {
      assert(new File(referenceBasePath + "." + fileEnding).exists(), "Could not find index file with file ending: " + fileEnding)
    }
  }

}