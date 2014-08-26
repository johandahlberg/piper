package molmed.report

import java.io.File
import java.util.jar.JarFile

/**
 * Generate reports from Piper, containing information on which Piper version
 * was used, which resources were used, etc.
 */
object ReportGenerator {

  /**
   * Parses the class path searching for the piper jar and checks it's version
   * from the manifest. Will fail misserably if there are multiple jar files
   * on the classpath where the file name start with "piper_".
   *
   * This will only work once everything has been packed into jar-files,
   * so it will return Unknown when testing e.g. in eclipse.
   *
   * @return the current piper version
   */
  def getPiperVersion(): String = {

    val classPath = System.getProperty("java.class.path").split(":")
    val jars = classPath.
      map(x => new File(x)).
      filter(s => s.getName().startsWith("piper_")).toSeq

    if (jars.size == 1) {
      val jarFile = new JarFile(jars(0).getAbsolutePath())

      val manifest = jarFile.getManifest()
      val attributes = manifest.getMainAttributes()
      val version = attributes.getValue("Implementation-Version")

      version
    } else
      "Unknown"

  }

  /**
   * Construct the report for the DNABestPracticeVariantCallingReport
   * qscript and write it to file.
   * @param file File to write output to.
   * @return the file that was created and written to.
   */
  def constructDNABestPracticeVariantCallingReport(file: File): File = {

    val piperVersion = getPiperVersion()
    val bwaVersion = "vervberv"
    val samtoolsVersion = "rbtbrtbtrbtrb"
    val qualimapVersion = "fbrtbrtbtr"
    val gatkVersion = "vtrrtbrtb"

    val reference = "vernveärovnärevn"
    val gatkBundleVersion = "wq161w6dq"
    val dbSNPVersion = "prvnäernb"
    val thousandGenomesIndelsVersion = "vervrev"
    val millsVersion = "vrevrev"

    val template =
      s"""
******
README
******

Data has been aligned to to the reference using bwa. The raw alignments have then been deduplicated, recalibrated and cleaned using GATK. Quality control information was gathered using Qualimap. Finally SNVs and indels have been called using the unified genotyper. The pipeline used was Piper, see below for more information.

The versions of programs and references used:
piper: $piperVersion
bwa: $bwaVersion
samtools: $samtoolsVersion
qualimap: $qualimapVersion     
gatk: $gatkVersion


gatk_bundle: $gatkBundleVersion
reference: $reference
db_snp: $dbSNPVersion
1000G_indels: $thousandGenomesIndelsVersion
Mills_and_1000G_golden_standard_indels: $millsVersion

piper
-----
Piper is a pipeline system developed and maintained at the National Genomics Infrastructure build on top of GATK Queue. For more information and the source code visit: www.github.com/NationalGenomicsInfrastructure/piper
      """

    println(template)

    //@TODO
    new File("")
  }

}