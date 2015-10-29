package molmed.utils

import org.broadinstitute.gatk.queue.QScript
import java.io.File

/**
 * The possible types of variant callers to use
 */
sealed trait VariantCallerOption
case object GATKUnifiedGenotyper extends VariantCallerOption
case object GATKHaplotypeCaller extends VariantCallerOption

/**
 * Config encapsulating all options for the variant calling.
 *
 * @param	qscript				the qscript to run the commandline wrappers in
 * @param variantCaller		The type of variant caller to use. Options are UnifiedGenotyper and HaplotypeCaller.
 * 							(default: HaplotypeCaller)
 * @param bams				the bam files to run on
 * @param outputDir			output dir
 * @param runSeparatly		Create one vcf per bam sample instead of running on full cohort
 * @param isLowPass			true if low pass
 * @param isExome				true if this is a exome
 * @param	noRecal				true if no recal to be done, e.g. if this is not a human sample
 * @param noIndels			true if indel calling should be skipped
 * @param testMode			true if running in test mode (skips adding dates to files)
 * @param downsampleFraction	downsample to this fraction of the reads (0-1) (Default: None)
 * @param minimumBaseQuality	the minimum base quality to be used for variant calling (Default: None)
 * @param deletions			maximum number of deletions at site to call snp (Default: None)
 * @param noBAQ				skip BAQ calculations
 * @param pcrFree				Indicated if the library was prepared using PCR or not (Default: None).
 * @param snpEffPath		Path to the startup script of snpEff
 * @param snpEffConfigPath  Path to snpEff config file
 * @param snpEffReference   The snpEff reference to use. E.g. "GRCh37.75"
 * @param skipAnnotation   Skip the annotation process
 * @param skipVcfCompression    Skip compression of generated vcf files
 */
case class VariantCallingConfig(qscript: QScript,
								variantCaller: Option[VariantCallerOption] = Some(GATKHaplotypeCaller),
                                bams: Seq[File],
                                outputDir: File,
                                runSeparatly: Boolean,
                                isLowPass: Boolean,
                                isExome: Boolean,
                                noRecal: Boolean,
                                noIndels: Boolean,
                                testMode: Boolean,
                                downsampleFraction: Option[Double] = None,
                                minimumBaseQuality: Option[Int] = None,
                                deletions: Option[Double] = None,
                                noBAQ: Boolean,
                                pcrFree: Option[Boolean] = None,
                                snpEffPath: Option[File] = None,
                                snpEffConfigPath: Option[File] = None,
                                snpEffReference: Option[String] = None,
                                skipAnnotation: Boolean,
                                skipVcfCompression: Boolean)