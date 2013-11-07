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

class GeneralUtils(projectName: Option[String], projId: String, uppmaxQoSFlag: Option[String]) extends UppmaxUtils(projId, uppmaxQoSFlag) {

  case class joinBams(inBams: Seq[File], outBam: File) extends MergeSamFiles with ExternalCommonArgs {
    this.input = inBams
    this.output = outBam

    this.analysisName = projectName.get + "_joinBams"
    this.jobName = projectName.get + "_joinBams"
    this.isIntermediate = false
  }

  case class writeList(inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    this.analysisName = projectName.get + "_bamList"
    this.jobName = projectName.get + "_bamList"
  }

  case class sortSam(inSam: File, outBam: File, sortOrderP: SortOrder) extends SortSam with ExternalCommonArgs {
    this.input :+= inSam
    this.output = outBam
    this.sortOrder = sortOrderP
    this.analysisName = projectName.get + "_sortSam"
    this.jobName = projectName.get + "_sortSam"
  }

  case class cutadapt(@Input fastq: File, cutFastq: File, @Argument adaptor: String, @Argument cutadaptPath: String, @Argument syncPath: String = "resources/FixEmptyReads.pl") extends SixGbRamJobs {
    
    @Output val fastqCut: File = cutFastq
    this.isIntermediate = true
    // Run cutadapt and sync via perl script by adding N's in all empty reads.  
    def commandLine = cutadaptPath + " -a " + adaptor + " " + fastq + " | perl " + syncPath + " -o " + fastqCut
    this.analysisName = projectName.get + "_cutadapt"
    this.jobName = projectName.get + "_cutadapt"
  }

  case class dedup(inBam: File, outBam: File, metricsFile: File) extends MarkDuplicates with ExternalCommonArgs {

    this.input :+= inBam
    this.output = outBam
    this.metrics = metricsFile
    this.memoryLimit = Some(16)
    this.analysisName = projectName.get + "_dedup"
    this.jobName = projectName.get + "_dedup"
  }

  case class validate(inBam: File, outLog: File, reference: File) extends ValidateSamFile with ExternalCommonArgs {
    this.input :+= inBam
    this.output = outLog
    this.REFERENCE_SEQUENCE = reference
    this.isIntermediate = false
    this.analysisName = projectName.get + "_validate"
    this.jobName = projectName.get + "_validate"
  }

  case class fixMatePairs(inBam: Seq[File], outBam: File) extends FixMateInformation with ExternalCommonArgs {
    this.input = inBam
    this.output = outBam
    this.analysisName = projectName.get + "_fixMates"
    this.jobName = projectName.get + "_fixMates"
  }

  case class revert(inBam: File, outBam: File, removeAlignmentInfo: Boolean) extends RevertSam with ExternalCommonArgs {
    this.output = outBam
    this.input :+= inBam
    this.removeAlignmentInformation = removeAlignmentInfo;
    this.sortOrder = if (removeAlignmentInfo) { SortOrder.queryname } else { SortOrder.coordinate }
    this.analysisName = projectName.get + "_revert"
    this.jobName = projectName.get + "_revert"
  }

  case class convertToFastQ(inBam: File, outFQ: File) extends SamToFastq with ExternalCommonArgs {
    this.input :+= inBam
    this.fastq = outFQ
    this.analysisName = projectName.get + "_convert2fastq"
    this.jobName = projectName.get + "_convert2fastq"
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