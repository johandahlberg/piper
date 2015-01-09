package molmed.config

/**
 * Contains the constants related to naming of resources such as programs
 * and reference files.
 * Since these are sometime read from config files (see the
 * FileAndProgramResourceConfig for an example) it's important that these
 * names are consistent across all classes. Use this object to make sure that
 * this is so!
 */
object Constants {

  val BWA = "bwa"
  val SAMTOOLS = "samtools"
  val QUALIMAP = "qualimap"
  val RNA_SEQC = "RNA-SeQC"
  val FIX_EMPTY_READS = "fixemptyreads"
  val CUFFLINKS = "cufflinks"
  val CUTADAPT = "cutadapt"
  val TOPHAP = "tophat"
  val SNP_EFF = "snpEff"

  val DB_SNP = "dbsnp"
  val INDELS = "indels"
  val HAPMAP = "hapmap"
  val OMNI = "omni"
  val MILLS = "mills"
  val THOUSAND_GENOMES = "thousandGenomes"
  val SNP_EFF_REFERENCE = "snpEffReference"  
    

  val unknown = "unknown"
}