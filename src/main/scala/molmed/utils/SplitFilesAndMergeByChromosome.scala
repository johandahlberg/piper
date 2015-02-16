package molmed.utils

import java.io.File
import scala.io.Source
import org.broadinstitute.gatk.queue.QScript

object SplitFilesAndMergeByChromosome {

  /**
   * Split the input bam file by the chromosomes defined in the accompanying
   * sequence dictionary.
   * @param qscript The QScript to run the splitting in.
   * @param bamFile
   * @param sequenceDictionary
   * @param generalUtils
   * @param asIntermediate
   * @return One file for each chromosome.
   */
  def splitByChromosome(
      qscript: QScript, 
      bamFile: File, 
      sequenceDictionaryFile: File, 
      generalUtils: GeneralUtils,
      asIntermediate: Boolean): Seq[File] = {

    val sequenceDicReader = Source.fromFile(sequenceDictionaryFile)

    // Sequence dicts have the following format:
    // @SQ  SN:chr1 LN:100000 UR:file:/humgen/gsa-scr1/hanna/src/StingWorking/exampleFASTA.fasta  M5:b52f0a0422e9544b50ac1f9d2775dc23
    // The part that we want is the "chr1"
    val sequenceDictionary =
      sequenceDicReader.getLines().
      filter { x => x.startsWith("@SQ") }.
      map {x => x.split("\\s+")(1).split(":")(1)}.toList    

    sequenceDicReader.close()
    
    // Split to separate files for each chromosome.
    for (chromosome <- sequenceDictionary) yield {
      val outputBamFile =
        GeneralUtils.swapExt(bamFile, ".bam", "_" + chromosome + ".bam")
      qscript.add(
          generalUtils.samtoolGetRegion(
              bamFile, 
              outputBamFile, 
              chromosome, 
              asIntermediate))
      outputBamFile
    }
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

}