package molmed.utils

import java.io.File

/**
 * Help class handling each variant calling target. Storing input files, creating output filenames etc.
 */
class VariantCallingTarget(outputDir: File,
                           val baseName: String,
                           val reference: File,
                           val bamList: Seq[File],
                           val intervals: Option[File],
                           val isLowpass: Boolean,
                           val isExome: Boolean,
                           val nSamples: Int,
                           val snpGenotypingVcf: Option[File] = None) {

  val name = outputDir + "/" + baseName
  val clusterFile = new File(name + ".clusters")
  val gVCFFile = new File(name + ".genomic.vcf")
  val rawSnpVCF = new File(name + ".raw.snp.vcf")
  val rawIndelVCF = new File(name + ".raw.indel.vcf")
  val rawCombinedVariants = new File(name + ".raw.vcf")
  val filteredIndelVCF = new File(name + ".filtered.indel.vcf")
  val recalibratedSnpVCF = new File(name + ".recalibrated.snp.vcf")
  val recalibratedIndelVCF = new File(name + ".recalibrated.indel.vcf")
  val tranchesSnpFile = new File(name + ".snp.tranches")
  val tranchesIndelFile = new File(name + ".indel.tranches")
  val vqsrSnpRscript: File = new File(name + ".snp.vqsr.r")
  val vqsrIndelRscript: File = new File(name + ".indel.vqsr.r")
  val recalSnpFile = new File(name + ".snp.tranches.recal")
  val recalIndelFile = new File(name + ".indel.tranches.recal")
  val evalFile = new File(name + ".snp.eval")
  val evalIndelFile = new File(name + ".indel.eval")
}
