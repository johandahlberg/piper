package molmed.utils

import java.io.File

case class GATKOptions(
  reference: File,
  nbrOfThreads: Option[Int] = Some(8),
  scatterGatherCount: Option[Int] = Some(1),
  intervalFile: Option[File],
  dbSNP: Option[Seq[File]],
  indels: Option[Seq[File]])