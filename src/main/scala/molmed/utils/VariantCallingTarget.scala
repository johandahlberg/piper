package molmed.utils

import java.io.File

  /**
   * Help class handling each variant calling target. Storing input files, creating output filenames etc.
   */
  class VariantCallingTarget(outputDir: String,
    val baseName: String,
    val reference: File,
    val bamList: Seq[File],
    val intervals: File,
    val isLowpass: Boolean,
    val isExome: Boolean,
    val nSamples: Int) {

    val name = if (outputDir.isEmpty()) baseName else outputDir + "/" + baseName
    val clusterFile = new File(name + ".clusters")
    val rawSnpVCF = new File(name + ".raw.snv.vcf")
    val rawIndelVCF = new File(name + ".raw.indel.vcf")
    val filteredIndelVCF = new File(name + ".filtered.indel.vcf")
    val recalibratedSnpVCF = new File(name + ".snp.recalibrated.snv.vcf")
    val recalibratedIndelVCF = new File(name + ".indel.recalibrated.vcf")
    val tranchesSnpFile = new File(name + ".snp.tranches")
    val tranchesIndelFile = new File(name + ".indel.tranches")
    val vqsrSnpRscript: File = new File(name + ".snp.vqsr.r")
    val vqsrIndelRscript: File = new File(name + ".indel.vqsr.r")
    val recalSnpFile = new File(name + ".snp.tranches.recal")
    val recalIndelFile = new File(name + ".indel.tranches.recal")
    val goldStandardRecalibratedVCF = new File(name + "goldStandard.recalibrated.vcf")
    val goldStandardTranchesFile = new File(name + "goldStandard.tranches")
    val goldStandardRecalFile = new File(name + "goldStandard.tranches.recal")
    val evalFile = new File(name + ".snp.eval")
    val evalIndelFile = new File(name + ".indel.eval")
    val goldStandardName = outputDir + "goldStandard/" + baseName
    val goldStandardClusterFile = new File(goldStandardName + ".clusters")
  }
