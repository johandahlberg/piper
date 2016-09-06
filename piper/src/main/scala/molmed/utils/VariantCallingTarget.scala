package molmed.utils

import java.io.File

/**
 * Help class handling each variant calling target. Storing input files, creating output filenames etc.
 */
class VariantCallingTarget(outputDir: File,
                           val baseName: String,
                           val reference: File,
                           val bamTargetList: Seq[GATKProcessingTarget],
                           val intervals: Option[File],
                           val isLowpass: Boolean,
                           val isExome: Boolean,
                           val nSamples: Int,
                           val snpGenotypingVcf: Option[File] = None,
                           val skipVcfCompression: Boolean = true) {

  val vcfExtension = if (skipVcfCompression) "vcf" else "vcf.gz"
  val name = outputDir + "/" + baseName
  val clusterFile = new File(name + ".clusters")
  val gVCFFile = new File(name + ".genomic." + vcfExtension)
  val rawSnpVCF = new File(name + ".raw.snp." + vcfExtension)
  val rawIndelVCF = new File(name + ".raw.indel." + vcfExtension)
  val rawCombinedVariants = new File(name + ".raw." + vcfExtension)
  val filteredIndelVCF = new File(name + ".filtered.indel." + vcfExtension)
  val recalibratedSnpVCF = new File(name + ".recalibrated.snp." + vcfExtension)
  val recalibratedIndelVCF = new File(name + ".recalibrated.indel." + vcfExtension)
  val tranchesSnpFile = new File(name + ".snp.tranches")
  val tranchesIndelFile = new File(name + ".indel.tranches")
  val vqsrSnpRscript: File = new File(name + ".snp.vqsr.r")
  val vqsrIndelRscript: File = new File(name + ".indel.vqsr.r")
  val recalSnpFile = new File(name + ".snp.tranches.recal")
  val recalIndelFile = new File(name + ".indel.tranches.recal")
  val combinedEvalFile = new File(name + ".eval")
  val evalFile = new File(name + ".snp.eval")
  val evalIndelFile = new File(name + ".indel.eval")
  val genotypeConcordance = new File(name + ".genotypeconcordance.txt")
}
