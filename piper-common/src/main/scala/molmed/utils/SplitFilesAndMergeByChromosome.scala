package molmed.utils

import java.io.File
import scala.io.Source
import org.broadinstitute.gatk.queue.QScript

object SplitFilesAndMergeByChromosome {

  /**
   * Group a series of chromosomes and their lengths by the lengths in an as
   * fare way as possible.
   *
   * @param nbrOfGroups
   * @param lengthAndChr
   * @return A sequence of sequences containing approximatly equally sized
   *         bins.
   */
  private def groupByLengthOfChromosome(nbrOfGroups: Int, lengthAndChr: Seq[(Long, String)]): Seq[Seq[String]] = {

    def lengthToCumulativeLength(x: Seq[(Long, String)]): Seq[(Long, String)] = {
      var cumSum: Long = 0
      x.map {
        case (chrLength, chr) => {
          cumSum = cumSum + chrLength
          (cumSum, chr)
        }
      }
    }

    val totalSizeOfGenome = lengthAndChr.map(_._1).reduce(_ + _)
    val groupSize = totalSizeOfGenome.toDouble / nbrOfGroups
    val sizeGroups =
      (0.0 to (groupSize * (nbrOfGroups)) by groupSize).toSeq.
        sliding(2).toList
    val cumLengthAndChr = lengthToCumulativeLength(lengthAndChr)

    val dividedByMatchingRange =
      sizeGroups.map {
        case list => {
          val lowerBound = list(0)
          val upperBound = list(1)
          val lengthsAndChrsInRange =

            cumLengthAndChr.filter {
              case (cumLength, chr) =>
                (lowerBound < cumLength) && (cumLength <= upperBound)
            }

          val chrs = lengthsAndChrsInRange.map(_._2)
          chrs
        }
      }

    // Remove any empty size ranges that might occur.
    dividedByMatchingRange.filter { x => !x.isEmpty }
  }

  /**
   * Split the input bam file by the chromosomes defined in the accompanying
   * sequence dictionary.
   * @param qscript The QScript to run the splitting in.
   * @param bamFile
   * @param waysToSplit how many ways to split the file.
   * @param sequenceDictionary
   * @param generalUtils
   * @param asIntermediate
   * @param samtoolsPath
   * @return One file for each chromosome.
   */
  def splitByChromosome(
    qscript: QScript,
    bamFile: File,
    sequenceDictionaryFile: File,
    waysToSplit: Int,
    generalUtils: GeneralUtils,
    asIntermediate: Boolean,
    samtoolsPath: String): Seq[File] = {

    val sequenceDicReader = Source.fromFile(sequenceDictionaryFile)

    // Sequence dicts have the following format:
    // @SQ  SN:chr1 LN:100000 UR:file:/humgen/gsa-scr1/hanna/src/StingWorking/exampleFASTA.fasta  M5:b52f0a0422e9544b50ac1f9d2775dc23
    // The part that we want is the "chr1" and the length LN
    val lenghtsAndChromosomes =
      sequenceDicReader.getLines().
        filter { x => x.startsWith("@SQ") }.
        map { x =>
          (x.split("\\s+")(2).split(":")(1).toLong,
            x.split("\\s+")(1).split(":")(1))
        }.toSeq

    val sequenceDictSplitted =
      groupByLengthOfChromosome(waysToSplit, lenghtsAndChromosomes)

    sequenceDicReader.close()

    // Split to separate files for each chromosome.
    val files =
      for (chromosomes <- sequenceDictSplitted) yield {

        val samtoolsRegionString = chromosomes.mkString(" ")
        val firstChromosome = chromosomes.head
        val lastChromosome = chromosomes.last

        val outputBamFile =
          GeneralUtils.swapExt(
            bamFile.getParentFile(),
            bamFile,
            ".bam",
            "_" + firstChromosome + "-" + lastChromosome + ".bam")

        qscript.add(
          generalUtils.samtoolGetRegion(
            bamFile,
            outputBamFile,
            samtoolsRegionString,
            asIntermediate,
            samtoolsPath))
        outputBamFile
      }

    files.toList
  }

  /**
   * Merge a set of bam files
   *
   * @param qscript
   * @param inBams
   * @param outBam
   * @param asIntermediate
   * @param generalUtils
   * @return The merged file
   */
  def merge(
    qscript: QScript,
    inBams: Seq[File],
    outBam: File,
    asIntermediate: Boolean,
    generalUtils: GeneralUtils): File = {

    qscript.add(
      generalUtils.joinBams(inBams, outBam, asIntermediate = asIntermediate))

    outBam
  }

  /**
   * Merge a set of recalibration table files
   *
   * @param qscript
   * @param inRecalTables
   * @param outRecalTable
   * @param asIntermediate
   * @param generalUtils
   * @return The merged file
   */
  def mergeRecalibrationTables(
    qscript: QScript,
    inRecalTables: Seq[File],
    outRecalTable: File,
    asIntermediate: Boolean,
    generalUtils: GeneralUtils): File = {

    qscript.add(
      generalUtils.mergeRecalibrationTables(inRecalTables, outRecalTable, asIntermediate = asIntermediate))

    outRecalTable
  }

}