package molmed.utils

import java.io.File

/**
 * Container class for GATK options and input files.
 */
case class GATKConfig(
  reference: File,
  nbrOfThreads: Option[Int] = Some(8),
  scatterGatherCount: Option[Int] = Some(1),
  intervalFile: Option[File],
  dbSNP: Option[File],
  indels: Option[Seq[File]],
  hapmap: Option[File] = None,
  omni: Option[File] = None,
  mills: Option[File] = None,
  thousandGenomes: Option[File] = None,
  notHuman: Boolean = false
  )