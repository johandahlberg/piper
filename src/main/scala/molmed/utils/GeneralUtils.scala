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

class GeneralUtils(qscript: QScript, projId: String, uppmaxQoSFlag: Option[String]) extends UppmaxUtils(projId, uppmaxQoSFlag) {

  case class joinBams(inBams: Seq[File], outBam: File, index: File) extends MergeSamFiles with ExternalCommonArgs {
    this.input = inBams
    this.output = outBam
    this.outputIndex = index

    this.analysisName = "joinBams"
    this.jobName = "joinBams"
    this.isIntermediate = false
  }

  case class writeList(inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    this.analysisName = "bamList"
    this.jobName = "bamList"
  }

  case class sortSam(inSam: File, outBam: File, sortOrderP: SortOrder) extends SortSam with ExternalCommonArgs {
    this.input :+= inSam
    this.output = outBam
    this.sortOrder = sortOrderP
    this.analysisName = "sortSam"
    this.jobName = "sortSam"
  }

  case class cutadapt(@Input fastq: File, @Output cutFastq: File, @Argument adaptor: String, @Argument cutadaptPath: String) extends SixGbRamJobs {
    this.isIntermediate = true
    // Run cutadapt and sync via perl script by adding N's in all empty reads.  
    def commandLine = cutadaptPath + " -a " + adaptor + " " + fastq + " | perl resources/FixEmptyReads.pl -o " + cutFastq

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