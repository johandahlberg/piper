package molmed.utils

import org.broadinstitute.sting.queue.QScript
import molmed.queue.setup.SampleAPI
import molmed.queue.setup.SetupXMLReaderAPI
import molmed.queue.setup.SetupXMLReader
import molmed.queue.setup.ReadPairContainer
import molmed.queue.setup.Sample
import org.broadinstitute.sting.queue.function.ListWriterFunction
import net.sf.samtools.SAMFileReader
import collection.JavaConversions._
import net.sf.samtools.SAMFileHeader.SortOrder
import org.broadinstitute.sting.queue.extensions.picard.MergeSamFiles
import org.broadinstitute.sting.queue.extensions.picard.SortSam
import org.broadinstitute.sting.queue.extensions.gatk.UnifiedGenotyper
import org.broadinstitute.sting.queue.extensions.gatk.CommandLineGATK
import org.broadinstitute.sting.gatk.downsampling.Downsampler
import org.broadinstitute.sting.gatk.downsampling.DownsamplingMethod
import org.broadinstitute.sting.gatk.downsampling.DownsampleType
import org.broadinstitute.sting.commandline.ArgumentException
import java.io.IOException
import org.broadinstitute.sting.queue.extensions.gatk.PrintReads
import org.broadinstitute.sting.queue.extensions.gatk.BaseRecalibrator
import org.broadinstitute.sting.queue.extensions.gatk.RealignerTargetCreator
import org.broadinstitute.sting.queue.extensions.gatk.IndelRealigner
import java.io.File

class Resources(resources: File, testMode: Boolean = false) {

  // For each resource get the matching file
  val dbsnp = getResourceFile(""".*dbsnp_137\.\w+\.vcf""")
  val hapmap = getResourceFile(""".*hapmap_3.3\.\w+\.vcf""")
  val omni = getResourceFile(""".*1000G_omni2.5\.\w+\.vcf""")
  val mills = getResourceFile(""".*Mills_and_1000G_gold_standard.indels\.\w+\.vcf""")
  val phase1 = getResourceFile(""".*1000G_phase1.indels\.\w+\.vcf""")

  //TODO When xml setup is implemented, get the path to the resource files from there.
  def allFilesInResourceFiles: Array[File] = {
    try {
      if (resources.exists())
        resources.getAbsoluteFile().listFiles()
      else
        throw new ArgumentException("Could not locate GATK bundle at: " + resources.getAbsolutePath())
    } catch {
      case e: ArgumentException => if (testMode) Array[File]() else throw e
    }
  }

  def getResourceFile(regexp: String): File = {
    val resourceFile: Array[File] = allFilesInResourceFiles.filter(file => file.getName().matches(regexp))

    try {
      if (resourceFile.length == 1)
        resourceFile(0)
      else if (resourceFile.length > 1)
        throw new IOException("Found more than one file matching regular expression: " + regexp + " found files: " + resourceFile.mkString(", "))
      else
        throw new IOException("Found no file matching regular expression: " + regexp)
    } catch {
      case e: IOException => if (testMode) new File("") else throw e
    }

  }
}