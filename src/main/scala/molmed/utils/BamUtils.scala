package molmed.utils

import java.io.File
import net.sf.samtools.SAMFileReader
import collection.JavaConversions._

object BamUtils {

    def getSampleNameFromReadGroups(bam: File): String = {
        val samFileReader = new SAMFileReader(bam)
        val samHeader = samFileReader.getFileHeader()
        val sampleNames = samHeader.getReadGroups().map(rg => rg.getSample())
        require(!sampleNames.isEmpty, "Couldn't find read groups in file: " + bam.getAbsolutePath() + ". This is required for the script to work.")
        require(sampleNames.length == 1, "More than one sample in file: " + bam.getAbsolutePath() +
            ". Please make sure that there is only one sample per file in input.")
        sampleNames(0)
    }

}