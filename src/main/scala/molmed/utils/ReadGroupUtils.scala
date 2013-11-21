package molmed.utils

import java.io.File
import net.sf.samtools.SAMFileHeader
import net.sf.samtools.SAMFileReader
import scala.collection.JavaConversions._

object ReadGroupUtils {

  def getSamHeaderFromFile(bam: File): SAMFileHeader = {
    val samFileReader = new SAMFileReader(bam)
    val samHeader = samFileReader.getFileHeader()
    samHeader
  }

  def getSampleNameFromReadGroups(bam: File): String = {
    val samHeader = getSamHeaderFromFile(bam)
    val sampleNames = samHeader.getReadGroups().map(rg => rg.getSample()).toSet
    require(!sampleNames.isEmpty, "Couldn't find read groups in file: " + bam.getAbsolutePath() + ". This is required for the script to work.")
    require(sampleNames.size == 1, "More than one sample in file: " + bam.getAbsolutePath() +
      ". Please make sure that there is only one sample per file in input.")
    sampleNames.toList(0)
  }

  def getLibraryNameFromReadGroups(bam: File): String = {
    val samHeader = getSamHeaderFromFile(bam)
    val library = samHeader.getReadGroups().map(rg => rg.getLibrary()).toSet
    require(!library.isEmpty, "Couldn't find read groups in file: " + bam.getAbsolutePath() + ". This is required for the script to work.")
    require(library.size == 1, "More than one library in file: " + bam.getAbsolutePath() +
      ". Please make sure that there is only one library per file in input.")
    library.toList(0)
  }
}