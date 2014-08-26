package molmed.config

import java.io.File

object FileVersionUtilities {

  type ResourceMap = Map[String, Option[Seq[VersionedFile]]]

  /**
   * Sticks a version on the file resource
   */
  case class VersionedFile(
    file: File,
    version: Option[String] = Some(Constants.unknown))

  implicit def versionedFile2File(x: VersionedFile): File =
    x.file

  implicit def seqOfVersionedFile2SeqOfFile(x: Seq[VersionedFile]): Seq[File] =
    x.map(y => versionedFile2File(y))

  def fileVersionFromKey(map: ResourceMap, key: String): String = {
    val versions = multipleFileVersionsFromKey(map, key)
    if (versions.size == 1)
      versions.head.version.getOrElse(Constants.unknown)
    else
      Constants.unknown
  }

  def multipleFileVersionsFromKey(map: ResourceMap, key: String): Seq[VersionedFile] = {
    val versions = map(key).getOrElse(Seq())
    versions
  }
}