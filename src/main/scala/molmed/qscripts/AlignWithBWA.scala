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

/**
 * TODO
 * - Fix core/node optimization.
 * 		This cannot be optimized further right now, as the the core programs require more memory. When uppmax upgrades, this will hopefully be possible.
 * - Look at the removing of intermediate bam failes part. Right now it seems that it removes the wrong files when re-running the script.
 */

class AlignWithBWA extends QScript {
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

  @Input(doc = "The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName = "path_to_bwa", shortName = "bwa", required = false)
  var bwaPath: File = _

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = "/usr/bin/samtools"

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "Decompose input BAM file and fully realign it using BWA and assume Single Ended reads", fullName = "use_bwa_single_ended", shortName = "bwase", required = false)
  var useBWAse: Boolean = false

  @Argument(doc = "Decompose input BAM file and fully realign it using BWA and assume Pair Ended reads", fullName = "use_bwa_pair_ended", shortName = "bwape", required = false)
  var useBWApe: Boolean = false

  @Argument(doc = "Decompose input BAM file and fully realign it using BWA SW", fullName = "use_bwa_sw", shortName = "bwasw", required = false)
  var useBWAsw: Boolean = false

  @Argument(doc = "Number of threads BWA should use", fullName = "bwa_threads", shortName = "bt", required = false)
  var bwaThreads: Int = 1

  @Argument(doc = "Perform validation on the BAM files", fullName = "validation", shortName = "vs", required = false)
  var validation: Boolean = false

  /**
   * **************************************************************************
   * Private variables
   * **************************************************************************
   */

  var projId: String = ""

  /**
   * **************************************************************************
   * Helper classes and methods
   * **************************************************************************
   */

  // Takes a list of processed BAM files and realign them using the BWA option requested  (bwase or bwape).
  // Returns a list of realigned BAM files.
  def performAlignment(fastqs: ReadPairContainer, readGroupInfo: String, reference: File, isIntermediateAlignment: Boolean = false): File = {

    val saiFile1 = new File(outputDir + fastqs.sampleName + ".1.sai")
    val saiFile2 = new File(outputDir + fastqs.sampleName + ".2.sai")
    //var alignedSamFile = new File(outputDir + fastqs.sampleName)
    val alignedBamFile = new File(outputDir + fastqs.sampleName + ".bam")

    // Align for single end reads
    if (useBWAse) {
      // Add jobs to the qgraph
      add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
        bwa_sam_se(fastqs.mate1, saiFile1, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))
    } // Align for paried end reads
    else if (useBWApe) {

      // Check that there is actually a mate pair in the container.
      assert(fastqs.isMatePaired())

      // Add jobs to the qgraph
      add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
        bwa_aln_se(fastqs.mate2, saiFile2, reference),
        bwa_sam_pe(fastqs.mate1, fastqs.mate2, saiFile1, saiFile2, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))
    } // Align for long single end reads using SW
    else if (useBWAsw) {
      // Add jobs to the qgraph
      add(bwa_sw(fastqs.mate1, alignedBamFile, reference, isIntermediateAlignment))
    }
    return alignedBamFile
  }

  /**
   * Check that all the files that make up bwa index exist for the reference.
   */
  private def checkReferenceIsBwaIndexed(reference: File): Unit = {
    assert(reference.exists(), "Could not find reference.")

    val referenceBasePath: String = reference.getAbsolutePath()
    for (fileEnding <- Seq("amb", "ann", "bwt", "pac", "sa")) {
      assert(new File(referenceBasePath + "." + fileEnding).exists(), "Could not find index file with file ending: " + fileEnding)
    }
  }

  private def alignSingleSample(sample: SampleAPI): File = {
    val fastqs = sample.getFastqs()
    val readGroupInfo = sample.getBwaStyleReadGroupInformationString()
    val reference = sample.getReference()

    // Check that the reference is indexed
    checkReferenceIsBwaIndexed(reference)

    // Run the alignment
    performAlignment(fastqs, readGroupInfo, reference)
  }

  /**
   * @TODO Write docs
   */
  private def alignMultipleSamples(sampleName: String, sampleList: Seq[SampleAPI]): File = {

    val expression = (""".*""" + sampleName + """\.ver\.(\d+)\.bam$""").r
    def getVersionOfPreviousAlignment(bam: File): Int = {
      expression.findFirstIn(bam.getAbsolutePath()).getOrElse(throw new Exception("Did not find version string.")).toInt
    }

    lazy val hasBeenSequenced: (Boolean, File) = {
      val listOfOutputFiles = new File(outputDir).list().toList
      if (listOfOutputFiles.exists(file => file.matches(expression.toString)))
        (true, new File(outputDir + "/" + listOfOutputFiles.find(file => 
          file.matches(expression.toString).getOrElse(throw new Exception("Did not find file.")))))
      else
        (false, null)
    }

    def findPlatformIds(bam: File): List[String] = {
      val samReader = new SAMFileReader(bam)
      val header = samReader.getFileHeader
      val readGroups = header.getReadGroups
      readGroups.map(f => f.getPlatformUnit()).toList
    }

    def align(sample: SampleAPI, asIntermidate: Boolean): File = {
      val fastqs = sample.getFastqs()
      val readGroupInfo = sample.getBwaStyleReadGroupInformationString()
      val reference = sample.getReference()

      // Add temporary run name
      fastqs.sampleName = sampleName + "." + sample.getReadGroupInformation.platformUnitId

      // Check that the reference is indexed
      checkReferenceIsBwaIndexed(reference)

      // Run the alignment
      performAlignment(fastqs, readGroupInfo, reference, asIntermidate)
    }

    val bam =
      if (hasBeenSequenced._1) {

        val previouslyJoinedBam = hasBeenSequenced._2
        val previouslyRunPlatformIds = findPlatformIds(previouslyJoinedBam)
        val nonRunSamples = sampleList.filter(p => previouslyRunPlatformIds.contains(p.getReadGroupInformation.platformUnitId))

        // Construct based on version of previous file
        val versionOfJoinedBam = getVersionOfPreviousAlignment(previouslyJoinedBam) + 1
        val newJoinedBam = new File(outputDir + "/" + sampleName + ".ver." + versionOfJoinedBam + ".bam")
        val newJoinedBamIndex = new File(outputDir + "/" + sampleName + ".ver." + versionOfJoinedBam + ".bai")

        if (nonRunSamples.length > 0) {
          val sampleSams: Seq[File] = for (sample <- nonRunSamples) yield {
            align(sample, false)
          }

          val filesToJoin = sampleSams :+ previouslyJoinedBam

          add(joinBams(filesToJoin, newJoinedBam, newJoinedBamIndex))
          add(removeIntermeditateFiles(Seq(previouslyJoinedBam)))
          newJoinedBam
        } else
          previouslyJoinedBam
      } else {

        val sampleSams: Seq[File] = for (sample <- sampleList) yield {
          align(sample, true)
        }

        val joinedBam = new File(outputDir + "/" + sampleName + ".ver.1.bam")
        val joinedBamIndex = new File(outputDir + "/" + sampleName + ".ver.1.bai")

        // Join and sort the sample bam files.
        add(joinBams(sampleSams, joinedBam, joinedBamIndex))
        joinedBam
      }
    bam
  }

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {
    // final output list of bam files
    var cohortList: Seq[File] = Seq()

    // Temporary solution to handle the case where there is a legacy setup file
    // which does not fulfill the xml-schema.
    val setupReader: SetupXMLReaderAPI =
      try {
        new SetupXMLReader(input)
      } catch {
        case e: Exception => new LegacySetupXMLReader(input)

      }

    val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
    projId = setupReader.getUppmaxProjectId()

    for ((sampleName, sampleList) <- samples) {

      // One sample can be sequenced in multiple lanes. This handles that scenario.
      val bam: File =
        if (sampleList.size == 1)
          alignSingleSample(sampleList.get(0))
        else
          alignMultipleSamples(sampleName, sampleList)

      // Add the resulting file of the alignment to the output list
      cohortList :+= bam
    }

    // output a BAM list with all the processed files
    val cohortFile = new File(qscript.outputDir + setupReader.getProjectName() + ".cohort.list")
    add(writeList(cohortList, cohortFile))
  }

  /**
   * **************************************************************************
   * Case classes - used by qgraph to setup the job run order.
   * **************************************************************************
   */

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {

    this.jobNativeArgs +:= "-p node -A " + projId
    this.memoryLimit = 24
    this.isIntermediate = false
  }

  trait SixGbRamJobs extends ExternalCommonArgs {
    this.jobNativeArgs +:= "-p core -n 2 -A " + projId
    this.memoryLimit = 6
  }

  case class joinBams(inBams: Seq[File], outBam: File, index: File) extends MergeSamFiles with ExternalCommonArgs {
    this.input = inBams
    this.output = outBam
    this.outputIndex = index

    this.analysisName = "joinBams"
    this.jobName = "joinBams"
    this.isIntermediate = false
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
  def sortAndIndex(alignedBam: File): String = " | " + samtoolsPath + " view -Su - | " + samtoolsPath + " sort - " + alignedBam.getAbsoluteFile().replace(".bam", "") + ";" +
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

  case class removeIntermeditateFiles(@Input files: Seq[File]) extends InProcessFunction {
    def run(): Unit = {
      files.foreach(f => {
        val success = f.delete()
        if (success)
          logger.debug("Successfully deleted intermediate file: " + f.getAbsoluteFile())
        else
          logger.error("Failed deleted intermediate file: " + f.getAbsoluteFile())
      })
    }
  }

  case class reNameFile(@Input inFile: File, @Output outFile: File) extends InProcessFunction {
    def run(): Unit = {
      inFile.renameTo(outFile)
    }
  }
}