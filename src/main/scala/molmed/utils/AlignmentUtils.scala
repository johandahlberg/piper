package molmed.utils

import java.io.File

import scala.collection.JavaConversions._

import org.broadinstitute.sting.commandline.Input
import org.broadinstitute.sting.commandline.Output
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.function.CommandLineFunction
import org.broadinstitute.sting.queue.function.InProcessFunction
import org.broadinstitute.sting.queue.function.ListWriterFunction

import molmed.queue.setup._
import molmed.queue.setup.ReadPairContainer
import molmed.queue.setup.SampleAPI
import molmed.utils.GeneralUtils.checkReferenceIsBwaIndexed
import net.sf.samtools.SAMFileHeader
import net.sf.samtools.SAMFileReader

import molmed.utils.ReadGroupUtils._

abstract class AligmentUtils(projectName: Option[String], projId: String, uppmaxQoSFlag: Option[String]) extends UppmaxUtils(projectName, projId, uppmaxQoSFlag)

class TophatAligmentUtils(tophatPath: String, tophatThreads: Int, projectName: Option[String], projId: String, uppmaxQoSFlag: Option[String]) extends AligmentUtils(projectName, projId, uppmaxQoSFlag) {

  case class tophat(fastqs1: File, fastqs2: File, sampleOutputDir: File, reference: File, annotations: Option[File], libraryType: String, outputFile: File, readGroupInfo: String, fusionSearch: Boolean = false) extends ExternalCommonArgs {

    // Sometime this should be kept, sometimes it shouldn't
    this.isIntermediate = false

    @Input var files1 = fastqs1
    @Input var files2 = fastqs2
    @Input var dir = sampleOutputDir
    @Input var ref = reference

    @Output var stdOut = outputFile

    val file1String = files1.getAbsolutePath()
    val file2String = if (files2 != null) files2.getAbsolutePath() else ""

    // Only add --GTF option if this has been defined as an option on the command line
    def annotationString = if (annotations.isDefined && annotations.get != null)
      " --GTF " + annotations.get.getAbsolutePath() + " "
    else
      ""

    // Only do fussion search if it has been defined on the command line.
    // Since it requires a lot of ram, make sure it requests a fat node.    
    def fusionSearchString = if (fusionSearch) {
      this.jobNativeArgs +:= "-p node -C fat -A " + projId
      this.memoryLimit = Some(48)
      " --fusion-search --bowtie1 --no-coverage-search "
    } else ""

    def commandLine = tophatPath +
      " --library-type " + libraryType +
      annotationString +
      " -p " + tophatThreads +
      " --output-dir " + dir +
      " " + readGroupInfo +
      " --keep-fasta-order " +
      fusionSearchString +
      ref + " " + file1String + " " + file2String +
      " 1> " + stdOut
  }
}

class BwaAlignmentUtils(qscript: QScript, bwaPath: String, bwaThreads: Int, samtoolsPath: String, projectName: Option[String], projId: String, uppmaxQoSFlag: Option[String]) extends AligmentUtils(projectName, projId, uppmaxQoSFlag) {

  // Takes a list of processed BAM files and realign them using the BWA option requested  (bwase or bwape).
  // Returns a list of realigned BAM files.
  def performAlignment(qscript: QScript)(fastqs: ReadPairContainer, readGroupInfo: String, reference: File, outputDir: File, isIntermediateAlignment: Boolean = false): File = {

    val saiFile1 = new File(outputDir + "/" + fastqs.sampleName + ".1.sai")
    val saiFile2 = new File(outputDir + "/" + fastqs.sampleName + ".2.sai")
    val alignedBamFile = new File(outputDir + "/" + fastqs.sampleName + ".bam")

    if (fastqs.isMatePaired) {
      // Add jobs to the qgraph
      qscript.add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
        bwa_aln_se(fastqs.mate2, saiFile2, reference),
        bwa_sam_pe(fastqs.mate1, fastqs.mate2, saiFile1, saiFile2, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))
    } else {
      qscript.add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
        bwa_sam_se(fastqs.mate1, saiFile1, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))
    }

    alignedBamFile
  }

  def align(sample: SampleAPI, outputDir: File, asIntermidate: Boolean): File = {

    val sampleName = sample.getSampleName()
    val fastqs = sample.getFastqs()
    val readGroupInfo = sample.getBwaStyleReadGroupInformationString()
    val reference = sample.getReference()

    // Add uniq name for run
    fastqs.sampleName = sampleName + "." + sample.getReadGroupInformation.platformUnitId

    // Check that the reference is indexed
    checkReferenceIsBwaIndexed(reference)

    // Run the alignment
    performAlignment(qscript)(fastqs, readGroupInfo, reference, outputDir, asIntermidate)
  }

  // Find suffix array coordinates of single end reads
  case class bwa_aln_se(fastq1: File, outSai: File, reference: File) extends SixGbRamJobs {
    @Input(doc = "fastq file to be aligned") var fastq = fastq1
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output sai file") var sai = outSai

    this.isIntermediate = true

    def commandLine = bwaPath + " aln -t " + bwaThreads + " -q 5 " + ref + " " + fastq + " > " + sai
    this.analysisName = projId + "bwa_aln"
    this.jobName = projId + "bwa_aln"
  }

  // Help function to create samtools sorting and indexing paths
  def sortAndIndex(alignedBam: File): String = " | " + samtoolsPath + " view -Su - | " + samtoolsPath + " sort - " + alignedBam.getAbsolutePath().replace(".bam", "") + ";" +
    samtoolsPath + " index " + alignedBam.getAbsoluteFile()

  // Perform alignment of single end reads
  case class bwa_sam_se(fastq: File, inSai: File, outBam: File, readGroupInfo: String, reference: File, intermediate: Boolean = false) extends ExternalCommonArgs {
    @Input(doc = "fastq file to be aligned") var mate1 = fastq
    @Input(doc = "bwa alignment index file") var sai = inSai
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output aligned bam file") var alignedBam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    def commandLine = bwaPath + " samse " + ref + " " + sai + " " + mate1 + " -r " + readGroupInfo +
      sortAndIndex(alignedBam)
    this.analysisName = "bwa_sam_se"
    this.jobName = "bwa_sam_se"
  }

  // Perform alignment of paired end reads
  case class bwa_sam_pe(fastq1: File, fastq2: File, inSai1: File, inSai2: File, outBam: File, readGroupInfo: String, reference: File, intermediate: Boolean = false) extends SixGbRamJobs {
    @Input(doc = "fastq file with mate 1 to be aligned") var mate1 = fastq1
    @Input(doc = "fastq file with mate 2 file to be aligned") var mate2 = fastq2
    @Input(doc = "bwa alignment index file for 1st mating pair") var sai1 = inSai1
    @Input(doc = "bwa alignment index file for 2nd mating pair") var sai2 = inSai2
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output aligned bam file") var alignedBam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    def commandLine = bwaPath + " sampe " + ref + " " + sai1 + " " + sai2 + " " + mate1 + " " + mate2 +
      " -r " + readGroupInfo +
      sortAndIndex(alignedBam)
    this.analysisName = "bwa_sam_pe"
    this.jobName = "bwa_sam_pe"
  }

  // Perform Smith-Watherman aligment of single end reads
  case class bwa_sw(inFastQ: File, outBam: File, reference: File, intermediate: Boolean = false) extends ExternalCommonArgs {
    @Input(doc = "fastq file to be aligned") var fq = inFastQ
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output bam file") var bam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    def commandLine = bwaPath + " bwasw -t " + bwaThreads + " " + ref + " " + fq +
      sortAndIndex(bam)
    this.analysisName = "bwasw"
    this.jobName = "bwasw"
  }
}