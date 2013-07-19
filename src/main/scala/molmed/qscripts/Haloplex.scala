package molmed.qscripts

import java.io.PrintWriter

import scala.collection.JavaConversions._
import scala.io.Source

import org.broadinstitute.sting.gatk.downsampling.DownsampleType
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk.BamGatherFunction
import org.broadinstitute.sting.queue.extensions.gatk.BaseRecalibrator
import org.broadinstitute.sting.queue.extensions.gatk.ClipReads
import org.broadinstitute.sting.queue.extensions.gatk.CommandLineGATK
import org.broadinstitute.sting.queue.extensions.gatk.IndelRealigner
import org.broadinstitute.sting.queue.extensions.gatk.RealignerTargetCreator
import org.broadinstitute.sting.queue.extensions.gatk.UnifiedGenotyper
import org.broadinstitute.sting.queue.extensions.gatk.VariantFiltration
import org.broadinstitute.sting.queue.extensions.gatk.VcfGatherFunction
import org.broadinstitute.sting.queue.extensions.picard.MergeSamFiles
import org.broadinstitute.sting.queue.extensions.picard.SortSam
import org.broadinstitute.sting.queue.function.ListWriterFunction

import molmed.queue.extensions.picard.CollectTargetedPcrMetrics
import molmed.queue.setup.ReadPairContainer
import molmed.queue.setup.Sample
import molmed.queue.setup.SampleAPI
import molmed.queue.setup.SetupXMLReader
import molmed.queue.setup.SetupXMLReaderAPI
import molmed.utils.Resources
import molmed.utils.Resources
import net.sf.samtools.SAMFileHeader
import net.sf.samtools.SAMFileHeader.SortOrder
import net.sf.samtools.SAMFileReader
import net.sf.samtools.SAMTextHeaderCodec

class Haloplex extends QScript {

  qscript =>

  /**
   * Arguments
   */

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "input pipeline setup xml", fullName = "input", shortName = "i", required = true)
  var input: File = _

  @Input(doc = "Location of resource files such as dbSnp, hapmap, etc.", fullName = "resources", shortName = "res", required = true)
  var resourcesPath: File = _

  @Input(doc = "bed files with haloplex intervals to be analyzed. (Covered from design package)", fullName = "gatk_interval_file", shortName = "intervals", required = true)
  var intervals: File = _

  @Input(doc = "Haloplex amplicons file", fullName = "amplicons", shortName = "amp", required = true)
  var amplicons: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName = "path_to_bwa", shortName = "bwa", required = false)
  var bwaPath: File = _

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = "/usr/bin/samtools"

  @Input(doc = "The path to the binary of butadapt", fullName = "path_to_cutadapt", shortName = "cutadapt", required = false)
  var cutadaptPath: File = _

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "Test mode", fullName = "test_mode", shortName = "test", required = false)
  var testMode: Boolean = false

  @Argument(doc = "How many ways to scatter/gather", fullName = "scatter_gather", shortName = "sg", required = false)
  var nContigs: Int = 23

  @Argument(doc = "Number of threads to use in thread enabled walkers. Default: 1", fullName = "nbr_of_threads", shortName = "nt", required = false)
  var nbrOfThreads: Int = 8

  /**
   * Private variables
   */

  private var uppmaxProjId: String = ""
  private var projectName: String = ""
  private var resources: Resources = null

  /**
   * Helper methods
   */

  def cutSamples(sampleMap: Map[String, Seq[SampleAPI]]): Map[String, Seq[SampleAPI]] = {

    // Standard Illumina adaptors    
    val adaptor1 = "AGATCGGAAGAGCACACGTCTGAACTCCAGTCAC"
    val adaptor2 = "AGATCGGAAGAGCGTCGTGTAGGGAAAGAGTGTAGATCTCGGTGGTCGCCGTATCATT"

    val cutadaptOutputDir = new File(outputDir + "/cutadapt")
    cutadaptOutputDir.mkdirs()

    // Run cutadapt & sync    

    def cutAndSyncSamples(samples: Seq[SampleAPI]): Seq[SampleAPI] = {

      def addSamples(sample: SampleAPI): SampleAPI = {

        def constructTrimmedName(name: String): String = {
          if (name.matches("fastq.gz"))
            name.replace("fastq.gz", "trimmed.fastq.gz")
          else
            name.replace("fastq", "trimmed.fastq.gz")
        }

        val readpairContainer = sample.getFastqs

        val mate1SyncedFastq = new File(cutadaptOutputDir + "/" + sample.getReadGroupInformation.platformUnitId + "/" + constructTrimmedName(sample.getFastqs.mate1.getName()))
        add(cutadapt(readpairContainer.mate1, mate1SyncedFastq, adaptor1))

        val mate2SyncedFastq =
          if (readpairContainer.isMatePaired) {
            val mate2SyncedFastq = new File(cutadaptOutputDir + "/" + sample.getReadGroupInformation.platformUnitId + "/" + constructTrimmedName(sample.getFastqs.mate2.getName()))
            add(cutadapt(readpairContainer.mate2, mate2SyncedFastq, adaptor2))
            mate2SyncedFastq
          } else null

        val readGroupContainer = new ReadPairContainer(mate1SyncedFastq, mate2SyncedFastq, sample.getSampleName)
        new Sample(sample.getSampleName, sample.getReference, sample.getReadGroupInformation, readGroupContainer)
      }

      val cutAndSyncedSamples = for (sample <- samples) yield { addSamples(sample) }
      cutAndSyncedSamples

    }

    val cutSamples = for { (sampleName, samples) <- sampleMap }
      yield (sampleName, cutAndSyncSamples(samples))

    cutSamples
  }

  // Takes a list of processed BAM files and realign them using the BWA option requested  (bwase or bwape).
  // Returns a list of realigned BAM files.
  def performAlignment(fastqs: ReadPairContainer, readGroupInfo: String, reference: File, isIntermediateAlignment: Boolean = false, outputDir: File): File = {

    val saiFile1 = new File(outputDir + "/" + fastqs.sampleName + ".1.sai")
    val saiFile2 = new File(outputDir + "/" + fastqs.sampleName + ".2.sai")
    val alignedBamFile = new File(outputDir + "/" + fastqs.sampleName + ".bam")

    // Check that there is actually a mate pair in the container.
    assert(fastqs.isMatePaired())

    // Add jobs to the qgraph
    add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
      bwa_aln_se(fastqs.mate2, saiFile2, reference),
      bwa_sam_pe(fastqs.mate1, fastqs.mate2, saiFile1, saiFile2, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))

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

  private def alignSingleSample(sample: SampleAPI, outputDir: File): File = {
    val fastqs = sample.getFastqs()
    val readGroupInfo = sample.getBwaStyleReadGroupInformationString()
    val reference = sample.getReference()

    // Check that the reference is indexed
    checkReferenceIsBwaIndexed(reference)

    // Run the alignment
    performAlignment(fastqs, readGroupInfo, reference, false, outputDir)
  }

  def getSamHeader(bam: File): SAMFileHeader = {
    val samReader = new SAMFileReader(bam)
    samReader.getFileHeader
  }

  def findPlatformIds(bam: File): List[String] = {
    val header = getSamHeader(bam)
    val readGroups = header.getReadGroups
    readGroups.map(f => f.getPlatformUnit()).toList
  }

  /**
   * @TODO Write docs
   */
  private def alignMultipleSamples(sampleName: String, sampleList: Seq[SampleAPI], outputDir: File): File = {

    val expression = (".*" + sampleName + "\\.ver\\.(\\d)\\.bam$").r
    def getVersionOfPreviousAlignment(bam: File): Int = {
      val matches = expression.findAllIn(bam.getName())
      if (matches.isEmpty)
        throw new Exception("Couldn't find match for version regexp." + expression)
      else
        matches.group(1).toInt
    }

    lazy val hasBeenSequenced: (Boolean, File) = {
      val listOfOutputFiles = new File(outputDir).list().toList
      if (listOfOutputFiles.exists(file => file.matches(expression.toString)))
        (true, new File(outputDir + "/" + listOfOutputFiles.find(file =>
          file.matches(expression.toString)).getOrElse(throw new Exception("Did not find file."))))
      else
        (false, null)
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
      performAlignment(fastqs, readGroupInfo, reference, asIntermidate, outputDir)
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
          add(removeIntermeditateFiles(Seq(previouslyJoinedBam), newJoinedBam))
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

  // Override the normal swapExt metod by adding the outputDir to the file path by default if it is defined.
  override def swapExt(file: File, oldExtension: String, newExtension: String) = {
    if (outputDir.isEmpty())
      new File(file.getName.stripSuffix(oldExtension) + newExtension)
    else
      swapExt(outputDir, file, oldExtension, newExtension);
  }

  /**
   * The actual script
   */

  def script() = {

    resources = new Resources(resourcesPath, testMode)

    // Create output dirs
    val vcfOutputDir = new File(outputDir + "/vcf_files")
    vcfOutputDir.mkdirs()
    val miscOutputDir = new File(outputDir + "/misc")
    miscOutputDir.mkdirs()
    val bamOutputDir = new File(outputDir + "/bam_files")
    bamOutputDir.mkdirs()

    // Get and setup input files
    val setupReader: SetupXMLReaderAPI = new SetupXMLReader(input)
    val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
    uppmaxProjId = setupReader.getUppmaxProjectId()
    projectName = setupReader.getProjectName

    // Run cutadapt       
    val cutAndSyncedSamples = cutSamples(samples)

    // Align with bwa
    val cohortList =
      for ((sampleName, sampleList) <- cutAndSyncedSamples) yield {

        // One sample can be sequenced in multiple lanes. This handles that scenario.
        val bam: File =
          if (sampleList.size == 1)
            alignSingleSample(sampleList(0), bamOutputDir)
          else
            alignMultipleSamples(sampleName, sampleList, bamOutputDir)

        // Add the resulting file of the alignment to the output list
        bam
      }

    // Make raw variation calls
    val preliminaryVariantCalls = new File(vcfOutputDir + "/" + projectName + ".pre.vcf")
    val reference = samples.values.flatten.toList(0).getReference
    add(genotype(cohortList.toSeq, reference, preliminaryVariantCalls, false))

    // Create realignment targets
    val targets = new File(miscOutputDir + "/" + projectName + ".targets.intervals")
    add(target(preliminaryVariantCalls, targets, reference))

    // Do indel realignment
    val postCleaningBamList =
      for (bam <- cohortList) yield {

        // Indel realignment
        val indelRealignedBam = swapExt(bamOutputDir, bam, ".bam", ".clean.bam")
        add(clean(Seq(bam), targets, indelRealignedBam, reference))
        indelRealignedBam
      }

    // BQSR
    val covariates = new File(miscOutputDir + "/" + outputDir + "/bqsr.grp")
    add(cov(postCleaningBamList.toSeq, covariates, reference))

    // Clip reads and apply BQSR
    val clippedAndRecalibratedBams =
      for (bam <- postCleaningBamList) yield {
        val clippedBam = swapExt(bamOutputDir, bam, ".bam", ".clipped.recal.bam")
        add(clip(bam, clippedBam, covariates, reference))
        clippedBam
      }

    // Convert intervals and amplicons from bed files to
    // picard metric files.
    val intervalsAsPicardIntervalFile = new File(swapExt(miscOutputDir, qscript.intervals, ".bed", ".intervals"))
    val ampliconsAsPicardIntervalFile = new File(swapExt(miscOutputDir, qscript.amplicons, ".bed", ".intervals"))

    add(convertCoveredToIntervals(qscript.intervals, intervalsAsPicardIntervalFile, clippedAndRecalibratedBams.toList(0)))
    add(convertAmpliconsToIntervals(qscript.amplicons, ampliconsAsPicardIntervalFile, clippedAndRecalibratedBams.toList(0)))

    // Collect targetedPCRMetrics
    // @TODO
    for (bam <- clippedAndRecalibratedBams) {
      val generalStatisticsOutputFile = swapExt(bamOutputDir, bam, ".bam", ".statistics")
      val perAmpliconStatisticsOutputFile = swapExt(bamOutputDir, bam, ".bam", ".amplicon.statistics")
      add(collectTargetedPCRMetrics(bam, generalStatisticsOutputFile, perAmpliconStatisticsOutputFile,
        ampliconsAsPicardIntervalFile, intervalsAsPicardIntervalFile, reference))
    }

    // Make variant calls
    val afterCleanupVariants = swapExt(vcfOutputDir, preliminaryVariantCalls, ".pre.vcf", ".vcf")
    add(genotype(clippedAndRecalibratedBams.toSeq, reference, afterCleanupVariants, true))

    // Filter variant calls
    val filteredCallSet = swapExt(vcfOutputDir, afterCleanupVariants, ".vcf", ".filtered.vcf")
    add(filterVariations(afterCleanupVariants, filteredCallSet, reference))

  }

  /**
   * Case class wappers for external programs
   */

  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {

    this.jobNativeArgs +:= "-p node -A " + uppmaxProjId
    this.memoryLimit = 24
    this.isIntermediate = false
  }

  // General arguments to GATK walkers
  trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {}

  case class cutadapt(@Input fastq: File, @Output cutFastq: File, @Argument adaptor: String) extends ExternalCommonArgs {

    this.isIntermediate = true

    this.jobNativeArgs +:= "-p core -n 2 -A " + uppmaxProjId
    this.memoryLimit = 6

    // Run cutadapt and sync via perl script by adding N's in all empty reads.  
    def commandLine = cutadaptPath + " -a " + adaptor + " " + fastq + " | perl resources/FixEmptyReads.pl -o " + cutFastq

  }

  trait SixGbRamJobs extends ExternalCommonArgs {
    this.jobNativeArgs +:= "-p core -n 2 -A " + uppmaxProjId
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

    def commandLine = bwaPath + " aln -t " + nbrOfThreads + " " + ref + " " + fastq + " > " + sai
    this.analysisName = projectName + "bwa_aln"
    this.jobName = projectName + "bwa_aln"
  }

  // Help function to create samtools sorting and indexing paths
  def sortAndIndex(alignedBam: File): String = " | " + samtoolsPath + " view -Su - | " + samtoolsPath + " sort - " + alignedBam.getAbsoluteFile().replace(".bam", "") + ";" +
    samtoolsPath + " index " + alignedBam.getAbsoluteFile()

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

    def commandLine = bwaPath + " sampe -A -P -s " + ref + " " + sai1 + " " + sai2 + " " + mate1 + " " + mate2 +
      " -r " + readGroupInfo +
      sortAndIndex(alignedBam)
    this.analysisName = "bwa_sam_pe"
    this.jobName = "bwa_sam_pe"
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

  case class removeIntermeditateFiles(@Input files: Seq[File], @Input placeHolder: File) extends InProcessFunction {
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

  def intervalFormatString(contig: String, start: String, end: String, strand: String, intervalName: String): String =
    "%s\t%s\t%s\t%s\t%s".format(contig, start, end, strand, intervalName)

  def formatFromCovered(split: Array[String]): String = {
    intervalFormatString(split(0), (split(1).toInt + 1).toString, split(2), "+", split(3))
  }

  def formatFromAmplicons(split: Array[String]): String = {
    intervalFormatString(split(0), (split(1).toInt + 1).toString, split(2), split(5), split(3))
  }

  def writeIntervals(bed: File, intervalFile: File, bam: File, formatFrom: Array[String] => String): Unit = {
    val header = getSamHeader(bam)
    header.setProgramRecords(List())
    header.setReadGroups(List())

    val writer = new PrintWriter(intervalFile)

    val codec = new SAMTextHeaderCodec();
    codec.encode(writer, header)

    for (row <- Source.fromFile(bed).getLines.drop(2)) {
      val split = row.split("\t")
      val intervalEntry = formatFrom(split)
      writer.println(intervalEntry)
    }
    writer.close()
  }

  case class convertCoveredToIntervals(@Input bed: File, @Output intervalFile: File, @Input bam: File) extends InProcessFunction {
    def run(): Unit = {
      writeIntervals(bed, intervalFile, bam, formatFromCovered)
    }
  }

  case class convertAmpliconsToIntervals(@Input bed: File, @Output intervalFile: File, @Input bam: File) extends InProcessFunction {
    def run(): Unit = {
      writeIntervals(bed, intervalFile, bam, formatFromAmplicons)
    }
  }

  case class genotype(@Input bam: Seq[File], reference: File, @Output @Gather(classOf[VcfGatherFunction]) vcf: File, isSecondPass: Boolean) extends UnifiedGenotyper with CommandLineGATKArgs {

    this.isIntermediate = false

    this.input_file = bam
    this.out = vcf

    this.dbsnp = resources.dbsnp
    this.reference_sequence = reference
    this.intervals = Seq(qscript.intervals)
    this.scatterCount = nContigs
    this.nt = nbrOfThreads
    this.stand_call_conf = 30.0
    this.stand_emit_conf = 10.0

    // Depending on if this is used to call preliminary or final variations different
    // parameters should be used.
    if (isSecondPass) {
      this.dt = DownsampleType.NONE
      this.annotation = Seq("AlleleBalance")
      this.filterMBQ = true
    } else {
      this.downsample_to_coverage = 30
    }

    this.output_mode = org.broadinstitute.sting.gatk.walkers.genotyper.UnifiedGenotyperEngine.OUTPUT_MODE.EMIT_VARIANTS_ONLY
    this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.BOTH
  }

  case class target(@Input candidateIndels: File, outIntervals: File, reference: File) extends RealignerTargetCreator with CommandLineGATKArgs {

    this.reference_sequence = reference
    this.num_threads = nbrOfThreads
    this.intervals = Seq(qscript.intervals)
    this.out = outIntervals
    this.mismatchFraction = 0.0
    this.known :+= resources.mills
    this.known :+= resources.phase1
    this.known :+= candidateIndels
    this.scatterCount = nContigs
    this.analysisName = outIntervals + ".target"
    this.jobName = outIntervals + ".target"
  }

  case class clean(inBams: Seq[File], tIntervals: File, outBam: File, reference: File) extends IndelRealigner with CommandLineGATKArgs {

    this.isIntermediate = true
    this.reference_sequence = reference
    this.input_file = inBams
    this.targetIntervals = tIntervals
    this.out = outBam
    this.known :+= resources.dbsnp
    this.known :+= resources.mills
    this.known :+= resources.phase1
    this.consensusDeterminationModel = org.broadinstitute.sting.gatk.walkers.indels.IndelRealigner.ConsensusDeterminationModel.KNOWNS_ONLY
    this.compress = 0
    this.scatterCount = nContigs
    this.analysisName = outBam + ".clean"
    this.jobName = outBam + ".clean"
  }

  case class cov(inBam: Seq[File], outRecalFile: File, reference: File) extends BaseRecalibrator with CommandLineGATKArgs {

    this.reference_sequence = reference
    this.isIntermediate = false

    this.num_cpu_threads_per_data_thread = nbrOfThreads

    this.knownSites :+= resources.dbsnp
    this.covariate ++= Seq("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "ContextCovariate")
    this.input_file = inBam
    this.disable_indel_quals = false
    this.out = outRecalFile
    this.intervals = Seq(qscript.intervals)

    this.scatterCount = nContigs
    this.analysisName = outRecalFile + ".covariates"
    this.jobName = outRecalFile + ".covariates"
  }

  case class clip(@Input inBam: File, @Output @Gather(classOf[BamGatherFunction]) outBam: File, covariates: File, reference: File) extends ClipReads with CommandLineGATKArgs {
    this.isIntermediate = false
    this.reference_sequence = reference
    this.input_file = Seq(inBam)
    this.out = outBam
    this.cyclesToTrim = "1-5"
    this.scatterCount = nContigs
    this.clipRepresentation = org.broadinstitute.sting.utils.clipping.ClippingRepresentation.WRITE_NS
    this.BQSR = covariates
  }

  case class filterVariations(@Input inVcf: File, @Output outVcf: File, reference: File) extends VariantFiltration with CommandLineGATKArgs {
    this.reference_sequence = reference
    this.variant = inVcf
    this.out = outVcf

    this.clusterWindowSize = 10
    this.clusterSize = 3
    this.filterExpression = Seq("MQ0 >= 4 && (( MQ0 / (1.0 * DP )) > 0.1)",
      "DP < 10",
      "QUAL < 30.0",
      "QUAL > 30.0 && QUAL < 50.0",
      "QD < 1.5")

    this.filterName = Seq("HARD_TO_VALIDATE", "LowCoverage", "VeryLowQual", "LowQual", "LowQD")

  }

  case class collectTargetedPCRMetrics(bam: File, generalStatisticsOutput: File, perTargetStat: File, ampliconIntervalFile: File, targetIntevalFile: File, ref: File) extends CollectTargetedPcrMetrics with ExternalCommonArgs {

    this.isIntermediate = false

    this.input = Seq(bam)
    this.output = generalStatisticsOutput
    this.perTargetOutputFile = perTargetStat
    this.amplicons = ampliconIntervalFile
    this.targets = targetIntevalFile
    this.reference = ref

  }

}