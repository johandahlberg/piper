package molmed.utils

import java.io.File
import scala.collection.JavaConversions._
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
import org.broadinstitute.sting.queue.util.StringFileConversions

/**
 * Base class for alignment workflows.
 */
abstract class AligmentUtils(projectName: Option[String], uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig)

	/**
	 * Holds classes and functions used for aligning with tophat
	 */
class TophatAligmentUtils(tophatPath: String, tophatThreads: Int, projectName: Option[String], uppmaxConfig: UppmaxConfig) extends AligmentUtils(projectName, uppmaxConfig) {

  /**
   * @param qscript						the qscript to in which the alignments should be used (usually "this")
   * @param libraryType				type of library that have been sequenced, fr-unstranded (default), fr-firststrand or fr-secondstrand
   * @param annotations				Annotations of known transcripts in GTF 2.2 or GFF 3 format.
   * @param outputDir					output it to this dir
   * @param sample						sample container that will be processed.
   * @param fusionSearch			perform fussion search using tophat
   * @return a bam file with aligned reads.
   */
  def align(qscript: QScript, libraryType: String, annotations: Option[File], 
    outputDir: File, sampleName: String, sample: SampleAPI,
    fusionSearch: Boolean): File = {
    
    val fastqs: ReadPairContainer = sample.getFastqs()
  	val sampleDir = new File(outputDir + "/" + sampleName)
    if(!sampleDir.exists()) {
    	sampleDir.mkdirs()
    }
    @Output var alignedBamFile: File = new File(sampleDir + "/" + "accepted_hits.bam")
    val outputLog = new File(sampleDir + "/qscript_tophap.stdout.log")
     
    if(fastqs.isMatePaired)
    {
      qscript.add(this.tophat(fastqs.mate1, fastqs.mate2, alignedBamFile, sampleDir, sample.getReference, annotations, libraryType, outputLog, sample.getTophatStyleReadGroupInformationString(), fusionSearch))
    }
    else
    {
      qscript.add(this.singleReadTophat(fastqs.mate1, alignedBamFile, sampleDir, sample.getReference, annotations, libraryType, outputLog, sample.getTophatStyleReadGroupInformationString(), fusionSearch))
    }
    return alignedBamFile  
  }
  
  /**
   * Base class for tophat. All general setting independent of wether it's single or double stranded alignment goes here.
   */
  abstract class tophatBase(sampleOutputDir: File, reference: File, annotations: Option[File], libraryType: String,
                            outputFile: File, readGroupInfo: String, fusionSearch: Boolean = false)
      extends EightCoreJob {
    // Sometime this should be kept, sometimes it shouldn't
    this.isIntermediate = false
    analysisName = "tophatBase"
    @Input var dir = sampleOutputDir
    @Input var ref = reference

    var stdOut = outputFile

    override def jobRunnerJobName = projectName.get + "_tophat"
    this.jobName = projectName.get + "_tophat"

    // Only add --GTF option if this has been defined as an option on the command line
    def annotationString = if (annotations.isDefined && annotations.get != null)
      " --GTF " + annotations.get.getAbsolutePath() + " "
    else
      ""

    // Only do fussion search if it has been defined on the command line.
    // Since it requires a lot of ram, make sure it requests a fat node.    
    def fusionSearchString = if (fusionSearch) {
      this.jobNativeArgs +:= "-p node -C fat -A " + uppmaxConfig.projId
      this.memoryLimit = Some(48)
      " --fusion-search --bowtie1 --no-coverage-search "
    } else ""
  }

  /**
   * Commandline wrapper for for single read alignment with tophat.
   */
  case class singleReadTophat(@Input fastqs1: File, @Output alignedBamFile: File, sampleOutputDir: File, reference: File, annotations: Option[File], libraryType: String,  outputFile: File, readGroupInfo: String, fusionSearch: Boolean = false)
      extends tophatBase(sampleOutputDir, reference, annotations, libraryType, outputFile, readGroupInfo, fusionSearch) {
  	analysisName = "singleReadTophat"
    @Input var files1 = fastqs1

    val file1String = files1.getAbsolutePath()

    def commandLine = tophatPath +
      " --library-type " + libraryType +
      annotationString +
      " -p " + tophatThreads +
      " --output-dir " + dir +
      " " + readGroupInfo +
      " --keep-fasta-order " +
      fusionSearchString +
      ref + " " + file1String +
      " 1> " + stdOut

  }

  /**
   * Commandline wrapper for for paired end alignment with tophat.
   */
  case class tophat(@Input fastqs1: File,@Input  fastqs2: File, @Output alignedBamFile: File, sampleOutputDir: File, reference: File, annotations: Option[File], libraryType: String, outputFile: File, readGroupInfo: String, fusionSearch: Boolean = false)
      extends tophatBase(sampleOutputDir, reference, annotations, libraryType, outputFile, readGroupInfo, fusionSearch) {
  	analysisName = "Tophat"
    @Input var files1 = fastqs1
    @Input var files2 = fastqs2

    val file1String = files1.getAbsolutePath()
    val file2String = if (files2 != null) files2.getAbsolutePath() else ""

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

// Possible alignment options.
sealed trait AlignerOption
case object BwaMem extends AlignerOption
case object BwaAln extends AlignerOption

/**
 * Utility classes and functions for running bwa
 */
class BwaAlignmentUtils(qscript: QScript, bwaPath: String, bwaThreads: Int, samtoolsPath: String, projectName: Option[String], uppmaxConfig: UppmaxConfig) extends AligmentUtils(projectName, uppmaxConfig) {

  /**
   * @param qscript						the qscript to in which the alignments should be used (usually "this")
   * @param fastqs						the read pair container with the fastq files
   * @param readGroupInfo				read group info to be added to bam file
   * @param	reference					reference to align to
   * @param outputDir					output it to this dir
   * @param isIntermediateAlignment		true if the files should be deleted when dependents have been run.
   * @param aligned						Aligner to use
   * @return a bam file with aligned reads.
   */
  private def performAlignment(qscript: QScript)(fastqs: ReadPairContainer,
                                                 readGroupInfo: String,
                                                 reference: File,
                                                 outputDir: File,
                                                 isIntermediateAlignment: Boolean = false,
                                                 aligner: Option[AlignerOption]): File = {

    val saiFile1 = new File(outputDir + "/" + fastqs.sampleName + ".1.sai")
    val saiFile2 = new File(outputDir + "/" + fastqs.sampleName + ".2.sai")
    val alignedBamFile = new File(outputDir + "/" + fastqs.sampleName + ".bam")

    aligner match {
      case Some(BwaAln) => {
        if (fastqs.isMatePaired) {
          // Add jobs to the qgraph
          qscript.add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
            bwa_aln_se(fastqs.mate2, saiFile2, reference),
            bwa_sam_pe(fastqs.mate1, fastqs.mate2, saiFile1, saiFile2, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))
        } else {
          qscript.add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
            bwa_sam_se(fastqs.mate1, saiFile1, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))
        }
      }
      case Some(BwaMem) => {
        if (fastqs.isMatePaired) {
          qscript.add(bwa_mem(fastqs.mate1, Some(fastqs.mate2), alignedBamFile, readGroupInfo, reference, intermediate = isIntermediateAlignment))
        } else {
          qscript.add(bwa_mem(fastqs.mate1, None, alignedBamFile, readGroupInfo, reference, intermediate = isIntermediateAlignment))
        }
      }
      case None => throw new Exception("No Aligner was set in performAlignment(...)!")
    }

    alignedBamFile
  }

  /**
   * @param 	sample			sample to align
   * @param	outputDir		output dir to use
   * @param	asIntermidiate	should this be kept of not
   * @param aligner			Aligner to be used. Defaults to BwaMem.
   * @returns a aligned bam file.
   */
  def align(sample: SampleAPI, outputDir: File, asIntermidate: Boolean, aligner: Option[AlignerOption] = Some(BwaMem)): File = {

    val sampleName = sample.getSampleName()
    val fastqs = sample.getFastqs()
    val readGroupInfo = sample.getBwaStyleReadGroupInformationString()
    val reference = sample.getReference()

    // Add uniq name for run
    fastqs.sampleName = sampleName + "." + sample.getReadGroupInformation.platformUnitId

    // Check that the reference is indexed
    checkReferenceIsBwaIndexed(reference)

    // Run the alignment
    performAlignment(qscript)(fastqs, readGroupInfo, reference, outputDir, asIntermidate, aligner)
  }

  // Find suffix array coordinates of single end reads
  case class bwa_aln_se(fastq1: File, outSai: File, reference: File) extends EightCoreJob {
    @Input(doc = "fastq file to be aligned") var fastq = fastq1
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output sai file") var sai = outSai

    this.isIntermediate = true

    def commandLine = bwaPath + " aln -t " + bwaThreads + " -q 5 " + ref + " " + fastq + " > " + sai
    override def jobRunnerJobName = projectName.get + "_bwaAln"
  }

  // Help function to create samtools sorting and indexing paths
  def sortAndIndex(alignedBam: File): String = " | " + samtoolsPath + " view -Su - | " + samtoolsPath + " sort - " + alignedBam.getAbsolutePath().replace(".bam", "") + ";" +
    samtoolsPath + " index " + alignedBam.getAbsoluteFile()

  // Perform alignment of single end reads
  case class bwa_sam_se(fastq: File, inSai: File, outBam: File, readGroupInfo: String, reference: File, intermediate: Boolean = false) extends OneCoreJob {
    @Input(doc = "fastq file to be aligned") var mate1 = fastq
    @Input(doc = "bwa alignment index file") var sai = inSai
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output aligned bam file") var alignedBam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    def commandLine = bwaPath + " samse " + ref + " " + sai + " " + mate1 + " -r " + readGroupInfo +
      sortAndIndex(alignedBam)
    override def jobRunnerJobName = projectName.get + "_bwaSamSe"
  }

  // Perform alignment of paired end reads
  case class bwa_sam_pe(fastq1: File, fastq2: File, inSai1: File, inSai2: File, outBam: File, readGroupInfo: String, reference: File, intermediate: Boolean = false) extends OneCoreJob {
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
    override def jobRunnerJobName = projectName.get + "_bwaSamPe"
  }

  // Bwa mem alignment container.
  case class bwa_mem(fastq1: File, fastq2: Option[File],
                     outBam: File,
                     readGroupInfo: String,
                     reference: File,
                     nbrOfThreads: Int = 15,
                     intermediate: Boolean = false) extends SixteenCoreJob {

    def sortAndIndex(alignedBam: File): String = " | " + samtoolsPath + " view -Su - | " +
      samtoolsPath + " sort -@ " + nbrOfThreads + " -m 7G " +  
      " - " + alignedBam.getAbsolutePath().replace(".bam", "") + ";" +
      samtoolsPath + " index " + alignedBam.getAbsoluteFile()

    @Input(doc = "fastq file with mate 1 to be aligned") var mate1 = fastq1
    @Input(doc = "fastq file with mate 2 file to be aligned") var mate2 = fastq2
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output aligned bam file") var alignedBam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    // Setup paired end or single end case
    def mateString = if (fastq2.isDefined)
      " " + mate1 + " " + mate2.get + " "
    else
      " " + mate1 + " "

    def commandLine =
      bwaPath + " mem -M -t " + nbrOfThreads + " " +
        " -R " + readGroupInfo + " " +
        ref + mateString +
        sortAndIndex(alignedBam)

    override def jobRunnerJobName = projectName.get + "_bwaMem"
  }

  // Perform Smith-Watherman aligment of single end reads
  case class bwa_sw(inFastQ: File, outBam: File, reference: File, intermediate: Boolean = false) extends EightCoreJob {
    @Input(doc = "fastq file to be aligned") var fq = inFastQ
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output bam file") var bam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    def commandLine = bwaPath + " bwasw -t " + bwaThreads + " " + ref + " " + fq +
      sortAndIndex(bam)
    override def jobRunnerJobName = projectName.get + "_bwaSw"
  }
}
