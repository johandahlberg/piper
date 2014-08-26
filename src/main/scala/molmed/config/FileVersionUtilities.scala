package molmed.config

import java.io.File

object FileVersionUtilities {

  type ResourceMap = Map[String, Option[Seq[VersionedFile]]]

  /**
   * Sticks a version on the file resource
   */
  case class VersionedFile(
    file: File,
    version: Option[String] = Some("Unknown"))

  implicit def versionedFile2File(x: VersionedFile): File =
    x.file

  implicit def seqOfVersionedFile2SeqOfFile(x: Seq[VersionedFile]): Seq[File] =
    x.map(y => versionedFile2File(y))      
    
    
  def fileVersionFromKey(map: ResourceMap, key: String): String = {
    val versions = map(key).get.flatMap(x => x.version).toSeq
    assert(versions.size == 1, "Didn't find strictyly one version. Found: " +
        versions)
    versions.head
  }  
}