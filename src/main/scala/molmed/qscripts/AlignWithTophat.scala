package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import scala.xml._
import collection.JavaConversions._
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.function.ListWriterFunction
import molmed.queue.setup._
import java.io.File
import java.io.PrintWriter
import java.util.regex.Pattern
import org.broadinstitute.sting.commandline.Hidden
import molmed.utils.Uppmaxable
import molmed.utils.GeneralUtils
import molmed.utils.UppmaxUtils
import molmed.utils.TophatAligmentUtils

/**
 * Align paired end reads to a reference using Tophat. By default cutadapt is not
 * run, but using the "--cutadapt" will add that step. Can also experimentally do
 * fusion search, however, this does not seem to be useful in the results
 * it generates.
 *  
 * @TODO
 * - Add single end capabilities.
 */

class AlignWithTophat extends QScript with Uppmaxable {

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

  @Input(doc = "The path to the binary of tophat", fullName = "path_to_tophat", shortName = "tophat", required = false)
  var tophatPath: File = _

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = "/usr/bin/samtools"

  @Input(doc = "The path to the binary of butadapt", fullName = "path_to_cutadapt", shortName = "cutadapt", required = false)
  var cutadaptPath: File = _

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "Perform validation on the BAM files", fullName = "validation", shortName = "vs", required = false)
  var validation: Boolean = false

  @Argument(doc = "Number of threads tophat should use", fullName = "tophat_threads", shortName = "tt", required = false)
  var tophatThreads: Int = 1

  @Argument(doc = "library type. Options: fr-unstranded (default), fr-firststrand, fr-secondstrand", fullName = "library_type", shortName = "lib", required = false)
  var libraryType: String = "fr-unstranded"

  @Argument(doc = "Annotations of known transcripts in GTF 2.2 or GFF 3 format.", fullName = "annotations", shortName = "a", required = false)
  var annotations: Option[File] = None

  @Argument(doc = "Do fussion search using tophat", fullName = "fusionSearch", shortName = "fs", required = false)
  var fusionSearch: Boolean = false

  @Argument(doc = "Run cutadapt", fullName = "cutadapt", shortName = "ca", required = false)
  var runCutadapt: Boolean = false

  /**
   * Help methods
   */

  def performAlignment(tophatAligmentUtils: TophatAligmentUtils, sampleName: String, fastqs: ReadPairContainer, reference: File, readGroupInfo: String): (File, File) = {

    // All fastqs input to this function should be from the same sample
    // and should all be aligned to the same reference.
    val sampleDir = new File(outputDir + sampleName)
    sampleDir.mkdirs()
    var alignedBamFile: File = new File(sampleDir + "/" + "accepted_hits.bam")

    val placeHolderFile = new File(sampleDir + "/qscript_tophap.stdout.log")

    add(tophatAligmentUtils.tophat(fastqs.mate1, fastqs.mate2, sampleDir, reference, annotations, libraryType, placeHolderFile, readGroupInfo, fusionSearch))

    return (alignedBamFile, placeHolderFile)
  }

  private def alignSamples(sampleMap: Map[String, Seq[SampleAPI]], tophatUtils: TophatAligmentUtils): (Seq[File], Seq[File]) = {

    var cohortSeq: Seq[File] = Seq()
    var placeHolderSeq: Seq[File] = Seq()

    /**
     * Make sure that if there are several instances of a sample
     * they are aligned separately with folder names:
     * <original sample name>_<int>
     */
    for ((sampleName, samples) <- sampleMap) {
      if (samples.size == 1) {
        val (file, placeholder) = performAlignment(tophatUtils, sampleName, samples(0).getFastqs, samples(0).getReference, samples(0).getTophatStyleReadGroupInformationString)
        cohortSeq :+= file
        placeHolderSeq :+= placeholder
      } else {
        var counter = 1
        for (sample <- samples) {
          val (file, placeholder) = performAlignment(tophatUtils, sampleName + "_" + counter, sample.getFastqs, sample.getReference, sample.getTophatStyleReadGroupInformationString)
          counter += 1
          cohortSeq :+= file
          placeHolderSeq :+= placeholder
        }
      }
    }
    return (cohortSeq, placeHolderSeq)
  }

  def cutSamples(generalUtils: GeneralUtils, sampleMap: Map[String, Seq[SampleAPI]]): Map[String, Seq[SampleAPI]] = {

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
        add(generalUtils.cutadapt(readpairContainer.mate1, mate1SyncedFastq, adaptor1, this.cutadaptPath))

        val mate2SyncedFastq =
          if (readpairContainer.isMatePaired) {
            val mate2SyncedFastq = new File(cutadaptOutputDir + "/" + sample.getReadGroupInformation.platformUnitId + "/" + constructTrimmedName(sample.getFastqs.mate2.getName()))
            add(generalUtils.cutadapt(readpairContainer.mate2, mate2SyncedFastq, adaptor2, this.cutadaptPath))
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

  /**
   * The actual script
   */
  def script {

    val setupReader: SetupXMLReaderAPI = new SetupXMLReader(input)

    val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
    projId = setupReader.getUppmaxProjectId()
    uppmaxQoSFlag = setupReader.getUppmaxQoSFlag()
    projectName = setupReader.getProjectName()

    val generalUtils = new GeneralUtils(projectName, projId, uppmaxQoSFlag)
    val tophatUtils = new TophatAligmentUtils(tophatPath, tophatThreads, projectName, projId, uppmaxQoSFlag)
    val (cohortList, placeHolderList) =
      if (runCutadapt)
        alignSamples(cutSamples(generalUtils, samples), tophatUtils)
      else
        alignSamples(samples, tophatUtils)

    // output a BAM list with all the processed files
    val cohortFile = new File(qscript.outputDir + projectName.get + ".cohort.list")
    add(writeList(cohortList, cohortFile, placeHolderList))

  }

  /**
   * Case classes for running command lines
   */

  /**
   * Special list writer class which uses a place holder to make sure it waits for input.
   */
  case class writeList(inBams: Seq[File], outBamList: File, @Input placeHolder: Seq[File]) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    this.analysisName = projectName.get + "_bamList"
  }
}
