package molmed.qscripts.legacy

import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.commandline.Hidden
import molmed.utils.VariantCallingUtils
import molmed.utils.GATKConfig
import molmed.utils.VariantCallingUtils
import molmed.utils.UppmaxXMLConfiguration
import molmed.utils.VariantCallingTarget

/**
 * Run variant calling using GATK.
 *
 * TODO
 * - Clean up the argument list
 */

class VariantCalling extends QScript with UppmaxXMLConfiguration {
  qscript =>

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "input BAM file - or list of BAM files", fullName = "input", shortName = "i", required = true)
  var input: File = _

  @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = true)
  var reference: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Input(doc = "dbsnp ROD to use (must be in VCF format)", fullName = "dbsnp", shortName = "D", required = false) 
  var dbSNP: File = _ 
 
  @Input(doc = "extra VCF files to use as reference indels for Indel Realignment", fullName = "extra_indels", shortName = "indels", required = false) 
  var indels: Seq[File] = Seq() 
 
  @Input(doc = "HapMap file to use with variant recalibration.", fullName = "hapmap", shortName = "hm", required = false) 
  var hapmap: File = _ 
 
  @Input(doc = "Omni file fo use with variant recalibration ", fullName = "omni", shortName = "om", required = false) 
  var omni: File = _ 
 
  @Input(doc = "Mills indel file to use with variant recalibration", fullName = "mills", shortName = "mi", required = false) 
  var mills: File = _ 

  @Argument(doc = "If the project is a non-human project - which means that there are normally no resources available.", fullName = "not_human", shortName = "nh", required = false)
  var notHuman: Boolean = false

  @Argument(doc = "If the project is a low pass project", fullName = "lowpass", shortName = "lp", required = false)
  var isLowpass: Boolean = false

  @Argument(doc = "If the project is a exome sequencing project", fullName = "isExome", shortName = "ie", required = false)
  var isExome: Boolean = false

  @Input(doc = "an intervals file to be used by GATK - output bams at intervals only", fullName = "gatk_interval_file", shortName = "intervals", required = false)
  var intervals: File = _

  @Argument(doc = "Run the analysis for each bam file seperatly. By default all samples will be analyzed together", fullName = "analyze_separatly", shortName = "analyzeSeparatly", required = false)
  var runSeparatly = false

  @Argument(shortName = "outputDir", doc = "output directory", required = false)
  var outputDir: String = ""

  @Argument(shortName = "skipCalling", doc = "skip the calling part of the pipeline and only run VQSR on preset, gold standard VCF files", required = false)
  var skipCalling: Boolean = false

  @Argument(shortName = "runGoldStandard", doc = "run the pipeline with the goldstandard VCF files for comparison", required = false)
  var runGoldStandard: Boolean = false

  @Argument(shortName = "noBAQ", doc = "turns off BAQ calculation", required = false)
  var noBAQ: Boolean = false

  @Argument(shortName = "noIndels", doc = "do not call indels with the Unified Genotyper", required = false)
  var noIndels: Boolean = false

  @Argument(shortName = "noRecal", doc = "Skip recalibration", required = false)
  var noRecal: Boolean = false

  @Argument(shortName = "mbq", doc = "The minimum Phred-Scaled quality score threshold to be considered a good base.", required = false)
  var minimumBaseQuality: Int = -1

  @Argument(shortName = "deletions", doc = "Maximum deletion fraction allowed at a site to call a genotype.", required = false)
  var deletions: Double = -1

  @Hidden
  @Argument(doc = "How many ways to scatter/gather", fullName = "scatter_gather", shortName = "sg", required = false)
  var nContigs: Int = -1

  @Argument(doc = "Number of threads to use in thread enabled walkers. Default: 1", fullName = "nbr_of_threads", shortName = "nt", required = false)
  var nbrOfThreads: Int = 1

  @Argument(doc = "Downsample fraction. [0.0 - 1.0]", fullName = "downsample_to_fraction", shortName = "dtf", required = false)
  var downsampleFraction: Double = -1

  @Argument(doc = "Test mode", fullName = "test_mode", shortName = "test", required = false)
  var testMode: Boolean = false

  def script = {

    val bams = QScriptUtils.createSeqFromFile(input)

    // By default scatter over the contigs
    if (nContigs < 0)
      nContigs = scala.math.min(QScriptUtils.getNumberOfContigs(bams(0)), 23)

    val uppmaxConfig = loadUppmaxConfigFromXML()
    val gatkOptions = {
      implicit def file2Option(file: File) = if (file == null) None else Some(file)
      new GATKConfig(reference, nbrOfThreads, nContigs, intervals, dbSNP, Some(indels), hapmap, omni, mills)
    }    
    val variantCallingUtils = new VariantCallingUtils(gatkOptions, projectName, uppmaxConfig)

    val intervalOption = if(intervals == null) None else Some(intervals) 
    
    val targets = (runSeparatly, notHuman) match {
      case (true, false) => bams.map(bam => new VariantCallingTarget(outputDir, bam.getName(), reference, Seq(bam), intervalOption, isLowpass, isExome, 1))
      case (true, true) => bams.map(bam => new VariantCallingTarget(outputDir, bam.getName(), reference, Seq(bam), intervalOption, isLowpass, false, 1))
      case (false, true) => Seq(new VariantCallingTarget(outputDir, projectName.get, reference, bams, intervalOption, isLowpass, false, bams.size))
      case (false, false) => Seq(new VariantCallingTarget(outputDir, projectName.get, reference, bams, intervalOption, isLowpass, isExome, bams.size))
    }

    for (target <- targets) {
      if (!skipCalling) {
        if (!noIndels) {
          // Indel calling, recalibration and evaulation
          add(new variantCallingUtils.UnifiedGenotyperIndelCall(target, testMode, downsampleFraction))
          if (!noRecal) {
            add(new variantCallingUtils.IndelRecalibration(target))
            add(new variantCallingUtils.IndelCut(target))
            add(new variantCallingUtils.IndelEvaluation(target))
          }
        }
        // SNP calling, recalibration and evaluation
        add(new variantCallingUtils.UnifiedGenotyperSnpCall(target, testMode, downsampleFraction, minimumBaseQuality, deletions, noBAQ))
        if (!noRecal) {
          add(new variantCallingUtils.SnpRecalibration(target))
          add(new variantCallingUtils.SnpCut(target))
          add(new variantCallingUtils.SnpEvaluation(target))
        }
      }
    }

  }
}
