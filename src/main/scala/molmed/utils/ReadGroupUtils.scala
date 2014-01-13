package molmed.utils

import java.io.File
import net.sf.samtools.SAMFileHeader
import net.sf.samtools.SAMFileReader
import scala.collection.JavaConversions._

/**
 * Contains functions to extract read group information from a bam file.
 */
object ReadGroupUtils {

  /**
   * @param bam		a bam file
   * @return the SAMFileHeader
   */
  def getSamHeaderFromFile(bam: File): SAMFileHeader = {
    val samFileReader = new SAMFileReader(bam)
    val samHeader = samFileReader.getFileHeader()
    samHeader
  }

  /**
   * NOTE: This function demands that there is only one sample present in the bam file.
   * It will throw an exception otherwise.
   *
   * @param bam
   * @return the sample name.
   */
  def getSampleNameFromReadGroups(bam: File): String = {
    val samHeader = getSamHeaderFromFile(bam)
    val sampleNames = samHeader.getReadGroups().map(rg => rg.getSample()).toSet
    require(!sampleNames.isEmpty, "Couldn't find read groups in file: " + bam.getAbsolutePath() + ". This is required for the script to work.")
    require(sampleNames.size == 1, "More than one sample in file: " + bam.getAbsolutePath() +
      ". Please make sure that there is only one sample per file in input.")
    sampleNames.toList(0)
  }

  /**
   * NOTE: This function demands that there is only one library present in the bam file.
   * It will throw an exception otherwise.
   *
   * @param bam
   * @return the library.
   */
  def getLibraryNameFromReadGroups(bam: File): String = {
    val samHeader = getSamHeaderFromFile(bam)
    val library = samHeader.getReadGroups().map(rg => rg.getLibrary()).toSet
    require(!library.isEmpty, "Couldn't find read groups in file: " + bam.getAbsolutePath() + ". This is required for the script to work.")
    require(library.size == 1, "More than one library in file: " + bam.getAbsolutePath() +
      ". Please make sure that there is only one library per file in input.")
    library.toList(0)
  }
}