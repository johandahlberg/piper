package molmed.config

import java.io.File
import javax.xml.bind.JAXBContext
import molmed.xml.globalconfig.GlobalConfig
import java.io.StringReader
import scala.collection.JavaConversions._
import molmed.xml.globalconfig.Resource
import molmed.xml.globalconfig.Program
import molmed.config.FileVersionUtilities._

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

  @Input(doc = "The path to RNA-SeQC", shortName = "rnaseqc", fullName = "rna_seqc", required = false)
  var pathToRNASeQC: File = _

  @Input(doc = "The path to the perl script used correct for empty reads ", fullName = "path_sync_script", shortName = "sync", required = false)
  var syncPath: File = _

  @Input(doc = "The path to the binary of cufflinks", fullName = "path_to_cufflinks", shortName = "cufflinks", required = false)
  var cufflinksPath: File = _

  @Input(doc = "The path to the binary of cutadapt", fullName = "path_to_cutadapt", shortName = "cutadapt", required = false)
  var cutadaptPath: File = _

  @Input(doc = "The path to the binary of tophat", fullName = "path_to_tophat", shortName = "tophat", required = false)
  var tophatPath: File = _

  /**
   * Implicitly convert any File to Option File, as necessary.
   */
  implicit def file2Option(file: File) = if (file == null) None else Some(file)

  /**
   * Will load file resources from XML file. Any values set via the
   * commandline will not be overriden by this.
   * @param	xmlFile	A xml file conforming to the specification in GlobalConfigSchema.xsd
   * @param	doNotLoadDefaultResourceFiles Skip loading the default resource files.
   * 									  Will only get resource file explicitly from the
   *                                      commandline.
   * @returns A map from a resource key to a versioned file.
   */
  def configureResourcesFromConfigXML(
    xmlFile: File,
    doNotLoadDefaultResourceFiles: Boolean = false): ResourceMap = {

    /**
     * This trait is used to make sure that both programs
     * and file resources can be managed by the same code
     * downstream once they've been extended with this trait.
     */
    trait NameVersionAndPath {
      def getName(): String
      def getPath(): String
      def getVersion(): String
    }

    /**
     * Transform list of resources (with name and path) to a map from the
     * resource name to the file(s) associated with the resource.
     * @param 	list	A list of resources which have both name and path
     * @return A map with resource names as keys and file resources as values
     */
    def transformToNamePathMap[T](list: Seq[T with NameVersionAndPath]): ResourceMap = {
      list.groupBy(x => x.getName()).
        mapValues(x =>
          Some(x.map(f =>
            new VersionedFile(new File(f.getPath), Some(f.getVersion()))).toSeq)).
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
    def getFileFromKey(map: ResourceMap, key: String): File = {
      val value = map(key).
        getOrElse(
          throw new IllegalArgumentException("Couldn't find: \"" + key + " \" key in " + xmlFile + "."))

      assert(value.length == 1, "Tried to get a single path for key: \"" + key + "\" but found multiple hits.")
      value.head
    }

    /**
     * Extract the file version from the specified resource key.
     * @param map	from the resource name to the versioned file relating to that resource.
     * @param key	the key to look for
     * @throws IllegalArgumentException if one tries to look for a key that is no present.
     * @throws AssertionError if there is multiple hits for this key.
     * @returns The version related to the key.
     */
    def getVersionFromKey(map: ResourceMap, key: String): Option[String] = {
      val value = map(key).
        getOrElse(
          throw new IllegalArgumentException("Couldn't find: \"" + key + " \" key in " + xmlFile + "."))

      assert(value.length == 1, "Tried to get a single path for key: \"" + key + "\" but found multiple hits.")
      value.head.version
    }

    /**
     * Extract file(s) for a certain resource.
     * @param 	map		from the resource name to the file(s) relating to that resource.
     * @param 	key		the key to look for
     * @throws IllegalArgumentException if one tries to look for a key that is no present.
     * @throws AssertionError if there is multiple hits for this key.
     * @returns The file related to the key.
     */
    def getFileSeqFromKey(map: ResourceMap, key: String): Seq[VersionedFile] = {
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
    def setFileResources(config: GlobalConfig): ResourceMap = {

      val resources = config.getResources().getResource().map(f => {
        val res = new Resource with NameVersionAndPath
        res.setName(f.getName())
        res.setPath(f.getPath())
        res.setVersion(f.getVersion())
        res
      })

      val resourceNameToPathsMap = transformToNamePathMap(resources)

      if (this.dbSNP == null)
        this.dbSNP = getFileFromKey(resourceNameToPathsMap, Constants.DB_SNP)

      if (this.indels.isEmpty)
        this.indels = getFileSeqFromKey(resourceNameToPathsMap, Constants.INDELS)

      if (this.hapmap == null)
        this.hapmap = getFileFromKey(resourceNameToPathsMap, Constants.HAPMAP)

      if (this.omni == null)
        this.omni = getFileFromKey(resourceNameToPathsMap, Constants.OMNI)

      if (this.mills == null)
        this.mills = getFileFromKey(resourceNameToPathsMap, Constants.MILLS)

      if (this.thousandGenomes == null)
        this.thousandGenomes = getFileFromKey(resourceNameToPathsMap, Constants.THOUSAND_GENOMES)

      resourceNameToPathsMap
    }

    /**
     * Sets the program resources.
     *
     * @param config the new program resources
     */
    def setProgramResources(config: GlobalConfig): ResourceMap = {

      val programs = config.getPrograms().getProgram().map(f => {
        val prog = new Program with NameVersionAndPath
        prog.setName(f.getName())
        prog.setPath(f.getPath())
        prog.setVersion(f.getVersion())
        prog
      })

      val programNameToPathsMap = transformToNamePathMap(programs)

      if (this.bwaPath == null)
        this.bwaPath = getFileFromKey(programNameToPathsMap, Constants.BWA)

      if (this.samtoolsPath == null)
        this.samtoolsPath = getFileFromKey(programNameToPathsMap, Constants.SAMTOOLS)

      if (this.qualimapPath == null)
        this.qualimapPath = getFileFromKey(programNameToPathsMap, Constants.QUALIMAP)

      if (this.pathToRNASeQC == null)
        this.pathToRNASeQC = getFileFromKey(programNameToPathsMap, Constants.RNA_SEQC)

      if (this.syncPath == null)
        this.syncPath = getFileFromKey(programNameToPathsMap, Constants.FIX_EMPTY_READS)

      if (this.cufflinksPath == null)
        this.cufflinksPath = getFileFromKey(programNameToPathsMap, Constants.CUFFLINKS)

      if (this.cutadaptPath == null)
        this.cutadaptPath = getFileFromKey(programNameToPathsMap, Constants.CUTADAPT)

      if (this.tophatPath == null)
        this.tophatPath = getFileFromKey(programNameToPathsMap, Constants.TOPHAP)

      programNameToPathsMap

    }

    val context = JAXBContext.newInstance(classOf[GlobalConfig])
    val unmarshaller = context.createUnmarshaller()
    val reader = new StringReader(scala.io.Source.fromFile(xmlFile).mkString)
    val config = unmarshaller.unmarshal(reader).asInstanceOf[GlobalConfig]
    reader.close()

    val fileResources =
      if (!doNotLoadDefaultResourceFiles)
        setFileResources(config)
      else
        Map()
    val programResources = setProgramResources(config)

    fileResources ++ programResources

  }

}