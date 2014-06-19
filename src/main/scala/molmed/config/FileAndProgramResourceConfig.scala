package molmed.config

import java.io.File
import javax.xml.bind.JAXBContext
import molmed.xml.globalconfig.GlobalConfig
import java.io.StringReader
import scala.collection.JavaConversions._
import molmed.xml.globalconfig.Resource
import molmed.xml.globalconfig.Program

/**
 * Contains arguments for loading file resources (e.g. dbSNP). Also contains the
 * setResourcesFromConfigXML which allows all of these values to be loaded from
 *  a xml file conforming to the GlobalConfigSchema.xsd specification (see:
 *  src/main/resources/).
 */
trait FileAndProgramResourceConfig {

  @Input(doc = "XML configuration file containing system configuration. E.g. paths to resources and programs etc. " +
    "Any thing specified in this file will be overriden if this is specifically set on the commandline.",
    fullName = "global_config", shortName = "gc", required = false)
  var globalConfig: File = _

  /**
   * File resources relating to the human genome.
   */

  @Input(doc = "dbsnp ROD to use (must be in VCF format)", fullName = "dbsnp", shortName = "D", required = false)
  var dbSNP: File = _

  @Input(doc = "extra VCF files to use as reference indels for Indel Realignment", fullName = "extra_indels", shortName = "indels", required = false)
  var indels: Seq[File] = Seq()

  @Input(doc = "HapMap file to use with variant recalibration.", fullName = "hapmap", shortName = "hm", required = false)
  var hapmap: File = _

  @Input(doc = "Omni file fo use with variant recalibration ", fullName = "omni", shortName = "om", required = false)
  var omni: File = _

  @Input(doc = "Mills indel file to use with variant recalibration", fullName = "mills", shortName = "mi", required = false)
  var mills: File = _

  @Input(doc = "1000 Genomes high confidence SNP  file to use with variant recalibration", fullName = "thousandGenomes", shortName = "tg", required = false)
  var thousandGenomes: File = _

  /**
   * Paths to programs
   */

  @Input(doc = "The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName = "path_to_bwa", shortName = "bwa", required = false)
  var bwaPath: File = _

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = _

  @Input(doc = "The path to the binary of qualimap", fullName = "path_to_qualimap", shortName = "qualimap", required = false)
  var qualimapPath: File = _

  /**
   * Will load file resources from XML file. Any values set via the
   * commandline will not be overriden by this.
   * @param	xmlFile	A xml file conforming to the specification in GlobalConfigSchema.xsd
   * @returns Unit
   */
  def setResourcesFromConfigXML(xmlFile: File): Unit = {

    /**
     * This trait is used to make sure that both programs
     * and file resources can be managed by the same code
     * downstream once they've been extended with this trait.
     */
    trait NameAndPath {
      def getName(): String
      def getPath(): String
    }

    /**
     * Transform list of resources (with name and path) to a map from the
     * resource name to the file(s) associated with the resource.
     * @param 	list	A list of resources which have both name and path
     * @return A map with resource names as keys and file resources as values
     */
    def transformToNamePathMap[T](list: Seq[T with NameAndPath]): Map[String, Option[Seq[File]]] = {
      list.groupBy(x => x.getName()).
        mapValues(x => Some(x.map(f => new File(f.getPath)).toSeq)).
        withDefaultValue(None)
    }

    /**
     * Extract a file for a certain resource. Note that getFileSeqFromKey should be used
     * if you are looking a resource with multiple files.
     * @param 	map		from the resource name to the file relating to that resource.
     * @param 	key		the key to look for
     * @throws IllegalArgumentException if one tries to look for a key that is no present.
     * @throws AssertionError if there is multiple hits for this key.
     * @returns The file related to the key.
     */
    def getFileFromKey(map: Map[String, Option[Seq[File]]], key: String): File = {
      val value = map(key).
        getOrElse(
          throw new IllegalArgumentException("Couldn't find: \"" + key + " \" key in " + xmlFile + "."))

      assert(value.length == 1, "Tried to get a single path for key: \"" + key + "\" but found multiple hits.")
      value.head
    }

    /**
     * Extract file(s) for a certain resource.
     * @param 	map		from the resource name to the file(s) relating to that resource.
     * @param 	key		the key to look for
     * @throws IllegalArgumentException if one tries to look for a key that is no present.
     * @throws AssertionError if there is multiple hits for this key.
     * @returns The file related to the key.
     */
    def getFileSeqFromKey(map: Map[String, Option[Seq[File]]], key: String): Seq[File] = {
      val value = map(key).
        getOrElse(
          throw new IllegalArgumentException("Couldn't find: \"" + key + " \" key in " + xmlFile + "."))
      value
    }

    /**
     * Will set all file resources specified in the config,
     * but will not override them if they have been setup via the
     * commandline.
     * @param	config	The GlobalConfig instance containing all paths.
     * @retuns Unit
     */
    def setFileResources(config: GlobalConfig): Unit = {

      val resources = config.getResources().getResource().map(f => {
        val res = new Resource with NameAndPath
        res.setName(f.getName())
        res.setPath(f.getPath())
        res
      })

      val resourceNameToPathsMap = transformToNamePathMap(resources)

      if (this.dbSNP == null)
        this.dbSNP = getFileFromKey(resourceNameToPathsMap, "dbsnp")

      if (this.indels.isEmpty)
        this.indels = getFileSeqFromKey(resourceNameToPathsMap, "indels")

      if (this.hapmap == null)
        this.hapmap = getFileFromKey(resourceNameToPathsMap, "hapmap")

      if (this.omni == null)
        this.omni = getFileFromKey(resourceNameToPathsMap, "omni")

      if (this.mills == null)
        this.mills = getFileFromKey(resourceNameToPathsMap, "mills")

      if (this.thousandGenomes == null)
        this.thousandGenomes = getFileFromKey(resourceNameToPathsMap, "thousandGenomes")
    }

    /**
     * Sets the program resources.
     *
     * @param config the new program resources
     */
    def setProgramResources(config: GlobalConfig): Unit = {

      val programs = config.getPrograms().getProgram().map(f => {
        val prog = new Program with NameAndPath
        prog.setName(f.getName())
        prog.setPath(f.getPath())
        prog
      })

      val programNameToPathsMap = transformToNamePathMap(programs)

      if (this.bwaPath == null)
        this.bwaPath = getFileFromKey(programNameToPathsMap, "bwa")

      if (this.samtoolsPath == null)
        this.samtoolsPath = getFileFromKey(programNameToPathsMap, "samtools")

      if (this.qualimapPath == null)
        this.qualimapPath = getFileFromKey(programNameToPathsMap, "qualimap")
    }

    val context = JAXBContext.newInstance(classOf[GlobalConfig])
    val unmarshaller = context.createUnmarshaller()
    val reader = new StringReader(scala.io.Source.fromFile(xmlFile).mkString)
    val config = unmarshaller.unmarshal(reader).asInstanceOf[GlobalConfig]
    reader.close()

    setFileResources(config)
    setProgramResources(config)

  }

}