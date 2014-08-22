package molmed.apps.setupcreator

import java.io.File
import java.io.FileOutputStream
import scala.collection.JavaConversions._
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import molmed.xml.setup._
import molmed.apps.Sthlm2UUSNP
import molmed.queue.setup.IlluminaXMLReportReader
import molmed.queue.setup.ReportReader

object SetupUtils {

  /**
   * Create a XML-serializable project instance
   */
  def createProject(): Project = {
    val project = new Project
    project
  }

  /**
   * Construct the platform id (unique sample identifier) from the
   * sample info.
   */
  private def platformInfo(sampleInfo: Sthlm2UUSNP.SampleInfo): String = {
    sampleInfo.flowCellId + "." + sampleInfo.sampleName + "." + sampleInfo.lane
  }

  /**
   * Does this file match the assumed UUSNP format?
   * @file file
   */
  def matchesUUSnp(file: File): Boolean = {
    val sampleDir = file.getParentFile()
    val runfolderDir = sampleDir.getParentFile()

    sampleDir.getName().startsWith("Sample_") &&
      runfolderDir.listFiles().exists(p =>
        p.getName() == "report.xml" || p.getName() == "report.tsv")
  }

  /**
   * Does this file match the assumed IGN format?
   * Lazy - just check if the folder name begins with something
   * looking like a date.
   * @file file
   */
  def matchesIGN(file: File): Boolean = {
    val runfolderDir = file.getParentFile()
    runfolderDir.getName().matches("^\\d{6}_.*")
  }

  /**
   * Splits a set of files into a tupple with files matching UUSNP first
   * and files matching IGN last.
   */
  def splitByFormatType(files: Set[File]): (Set[File], Set[File]) = {

    //@TODO This approach will not work as it might only find one of the sets!

    val mappedToType =
      files.groupBy(file => {
        require(matchesIGN(file) || matchesUUSnp(file),
          "Didn't match any of the required formats.")
        matchesUUSnp(file)
      }).withDefaultValue(Set())

    val groups = mappedToType.keys.toSeq

    assert(groups.size <= 2,
      "Found more than two groups in" +
        " split. That shouldn't happen! Groups were: " +
        groups)

    (mappedToType(true), mappedToType(false))
  }

  /**
   * Set the meta data for a project
   * @param project	The project to a apply the meta data to.
   * @param projectName
   * @param	seqencingPlatform
   * @param sequencingCenter
   * @param uppmaxProjectId
   * @param uppmaxQoSFlag
   * @param reference
   * @return The project modified with the meta data.
   */
  def setMetaData(project: Project)(projectName: String,
                                    seqencingPlatform: String,
                                    sequencingCenter: String,
                                    uppmaxProjectId: String,
                                    uppmaxQoSFlag: String,
                                    reference: File): Project = {
    val projectMetaData = new Metadata()

    projectMetaData.setName(projectName)
    projectMetaData.setPlatform(seqencingPlatform)
    projectMetaData.setSequenceingcenter(sequencingCenter)
    projectMetaData.setUppmaxprojectid(uppmaxProjectId)
    projectMetaData.setUppmaxqos(uppmaxQoSFlag)
    projectMetaData.setReference(reference.getAbsolutePath())
    project.setMetadata(projectMetaData)

    project.setInputs(new Inputs)
    project
  }

  /**
   * Once the smapleInfoSet has been parsed, this class can be used
   * to create the xml hierarchy and add it to the project.
   * @param project The project to write to
   * @param sampleInfoSet The set of all samples
   */
  private def constructProjectHierarchy(
    project: Project,
    sampleInfoSet: Set[Sthlm2UUSNP.SampleInfo]): Project = {

    /**
     * Helper method which recursively will go down through the project
     * hierarchy and add fastq file leaves from the SampleInfo instances.
     * It will create and add any other nodes along the way that has
     * not already been created.
     *
     * Since the xjc generated xml elements are not immutable by nature
     * this method will do some stuff, like adding to lists, which will actually
     * change the state of the project. Be aware that this is happening and
     * check for side effects.
     *
     * @param x 		A part of the project hierarchy. Since this is not
     * 				    enforced by a class bound, don't send the wrong stuff
     *         		    in here!
     * @param project 	The current project to add to.
     * @param sampleInfo A sample
     * @return A project with the the info in sampleInfo added to it.
     *
     */
    def constructHelper(
      x: Any,
      project: Project,
      sampleInfo: Sthlm2UUSNP.SampleInfo): Project = {

      /**
       * See if there is a matching element in the collection
       * and create one if there is not.
       *
       * @param list
       * @param sampleInfo
       * @param predicate
       * @param constructNewA
       * @return The matching element
       */
      def findMatchingInCollection[A](
        list: java.util.List[A],
        sampleInfo: Sthlm2UUSNP.SampleInfo,
        predicate: (A, Sthlm2UUSNP.SampleInfo) => Boolean,
        constructNewA: (Sthlm2UUSNP.SampleInfo => A)): A = {

        list.find(x => predicate(x, sampleInfo)).
          getOrElse({
            constructNewA(sampleInfo)
          })

      }

      /**
       * Add the element x if it's not already in the list.
       * @param list
       * @param x
       * @param sampleInfo
       * @param predicate
       * @returns The list with the element added if it was not already there.
       */
      def addIfNotThere[B](
        list: java.util.List[B],
        x: B,
        sampleInfo: Sthlm2UUSNP.SampleInfo,
        predicate: (B, Sthlm2UUSNP.SampleInfo) => Boolean): java.util.List[B] = {

        if (!list.exists(p => predicate(p, sampleInfo))) {
          list.add(x)
          list
        } else
          list

      }

      x match {
        case x: Platformunit => {

          val fastqFiles = x.getFastqfile()

          fastqFiles.add({
            val fastqFile = new Fastqfile
            fastqFile.setPath(sampleInfo.fastq.getAbsolutePath())
            fastqFile
          })

          project
        }
        case x: Library => {

          val platformUnits = x.getPlatformunit()

          def predicate =
            (p: Platformunit, sampleInfo: Sthlm2UUSNP.SampleInfo) =>
              p.getUnitinfo() == platformInfo(sampleInfo)

          val platformUnit =
            findMatchingInCollection(
              platformUnits,
              sampleInfo,
              predicate,
              (sampleInfo: Sthlm2UUSNP.SampleInfo) => {
                val pu = new Platformunit
                pu.setUnitinfo(platformInfo(sampleInfo))
                pu
              })

          addIfNotThere(platformUnits, platformUnit, sampleInfo, predicate)

          constructHelper(platformUnit, project, sampleInfo)
        }
        case x: Sample => {
          val libraries = x.getLibrary()

          def predicate =
            (p: Library, sampleInfo: Sthlm2UUSNP.SampleInfo) =>
              p.getLibraryname() == sampleInfo.library

          val library =
            findMatchingInCollection(
              libraries,
              sampleInfo,
              predicate,
              (sampleInfo: Sthlm2UUSNP.SampleInfo) => {
                val l = new Library
                l.setLibraryname(sampleInfo.library)
                l
              })

          addIfNotThere(libraries, library, sampleInfo, predicate)

          constructHelper(library, project, sampleInfo)
        }
        case x: Inputs => {
          val samples = x.getSample()

          def predicate =
            (p: Sample, sampleInfo: Sthlm2UUSNP.SampleInfo) =>
              p.getSamplename() == sampleInfo.sampleName

          val sample =
            findMatchingInCollection(
              samples,
              sampleInfo,
              predicate,
              (s: Sthlm2UUSNP.SampleInfo) =>
                {
                  val s = new Sample
                  s.setSamplename(sampleInfo.sampleName)
                  s
                })

          addIfNotThere(samples, sample, sampleInfo, predicate)

          constructHelper(sample, project, sampleInfo)
        }
        case x: Project => {
          val inputs = x.getInputs()
          constructHelper(inputs, project, sampleInfo)
        }
      }

    }

    sampleInfoSet.foldLeft(project)((project, sampleInfo) => {
      constructHelper(project, project, sampleInfo)
    })

  }

  /**
   * This will parse a list of FASTQ files assuming the ign formatting (see
   * below) and create a project xml.
   * This is the assumed folder structure of a IGN project:
   *
   * Project
   * └── Sample
   *     └── Library Prep
   *         └── Sequencing Run
   *             ├── P1142_101_NoIndex_L002_R1_001.fastq.gz
   *             └── P1142_101_NoIndex_L002_R1_001.fastq.gz
   *
   *
   * @param The project to add the info to.
   * @param fileList a list of FASTQ files
   * @return a Project instance
   */
  def createXMLFromIGNHierarchy(project: Project)(fileList: Set[File]): Project = {

    def parseInfoFromFile(file: File): Sthlm2UUSNP.SampleInfo = {

      val runfolder = file.getParentFile()
      val libraryFolder = runfolder.getParentFile()
      val sampleFolder = libraryFolder.getParentFile()

      Sthlm2UUSNP.parseSampleInfoFromFileHierarchy(
        file, runfolder, libraryFolder)
    }

    val fileInfo = fileList.map(file => parseInfoFromFile(file))
    //createXMLFromFileInfo(project, fileInfo)
    constructProjectHierarchy(project, fileInfo)
  }

  /**
   * This will parse a list of FASTQ files assuming the uu snp formatting (see
   * below) and create a project xml.
   *
   * This is the assumed folder structure of a UU project:
   *
   * Project-code/
   * ├── 140403_SN866_0281_BC3U9JACXX
   * │   ├── checksums.md5
   * │   ├── Plots
   * │   ├── README
   * │   ├── report.html
   * │   ├── report.xml
   * │   ├── report.xsl
   * │   ├── Sample_1
   * │   ├── Sample_2
   * ├── 140416_D00118_0139_BC41ENACXX
   * │   ├── checksums.md5
   * │   ├── Plots
   * │   ├── README
   * │   ├── report.html
   * │   ├── report.xml
   * │   ├── report.xsl
   * │   ├── Sample_1
   * │   ├── Sample_2
   *
   * Please note that since the library information is not available in the
   * UUSNP folder structure, but is part of the report.xml, this function
   * will try to find any matching report.xml files and parse those for the
   * library information
   *
   * @param project The project to add the info to
   * @param fileList a list of FASTQ files
   * @return a Project instance
   */
  def createXMLFromUUHierarchy(project: Project)(fileList: Set[File]): Project = {

    val filesGroupedByRunfolderOrigin = fileList.groupBy(file => {
      val sampleDir = file.getParentFile()
      val runfolder = sampleDir.getParentFile()
      runfolder
    })

    val fileInfo =
      for {
        (runfolder, fastqFiles) <- filesGroupedByRunfolderOrigin
        fastqFile <- fastqFiles
      } yield {

        val fastqFileRegexp =
          """^(\w+)_(\w+(?:-\w+)?)_L(\d+)_R(\d)_(\d+)\.fastq\.gz$""".r

        val sampleName =
          fastqFile.getParentFile().getName().replaceFirst("Sample_", "")

        val lane = {
          val lane = fastqFileRegexp.findAllIn(fastqFile.getName()).
            matchData.map(m => m.group(3).toInt).toSeq
          require(
            lane.size == 1,
            "Lane list size not 1, something went wrong." +
              "Lane list was: " + lane.toList +
              "file was:" + fastqFile.getName())
          lane(0)
        }

        val library = {
          val reportReader =
            ReportReader(
              new File(runfolder.getAbsolutePath() + "/report.xml"))
          reportReader.getReadLibrary(sampleName, lane)
        }

        val runfolderNameSplitByUnderscore = runfolder.getName().split("_")
        val date = runfolderNameSplitByUnderscore(0)
        val flowCellId =
          runfolderNameSplitByUnderscore(runfolderNameSplitByUnderscore.size - 1)

        val index = {
          val index = fastqFileRegexp.findAllIn(fastqFile.getName()).
            matchData.map(m => m.group(2)).toSeq
          require(
            index.size == 1,
            "index list size not 1, something went wrong.")
          index(0)
        }

        val read = {
          val read = fastqFileRegexp.findAllIn(fastqFile.getName()).
            matchData.map(m => m.group(4).toInt).toSeq
          require(
            read.size == 1,
            "read list size not 1, something went wrong.")
          read(0)
        }

        new Sthlm2UUSNP.SampleInfo(
          sampleName,
          library,
          lane,
          date,
          flowCellId,
          index,
          fastqFile,
          read)
      }

    //createXMLFromFileInfo(project, fileInfo.toSet)
    constructProjectHierarchy(project, fileInfo.toSet)
  }

  /**
   * Writes the project to a specified xml file.
   * @param project		The project to write
   * @param outputFile	The file to write to.
   */
  def writeToFile(project: Project, outputFile: File) = {
    // The xml marshaller is used to create the xml instance
    val context = JAXBContext.newInstance(classOf[Project])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val os = new FileOutputStream(outputFile)
    marshaller.marshal(project, os)
    os.close()

  }

  /**
   * Writes the project to stdout. Useful for debugging.
   * @param project		The project to write
   * @param outputFile	The file to write to.
   */
  def writeToStdOut(project: Project) = {
    // The xml marshaller is used to create the xml instance
    val context = JAXBContext.newInstance(classOf[Project])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val os = new FileOutputStream(new File("/dev/stdout"))
    marshaller.marshal(project, os)
    os.close()

  }
}