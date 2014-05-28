package molmed.qscripts

import org.broadinstitute.sting.queue.QScript
import scala.xml._
import collection.JavaConversions._
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.function.ListWriterFunction
import molmed.queue.setup._
import java.io.File
import java.io.PrintWriter
import java.util.regex.Pattern
import org.broadinstitute.sting.commandline.Hidden
import molmed.utils.Uppmaxable
import molmed.utils.GeneralUtils
import molmed.utils.UppmaxJob
import molmed.utils.TophatAligmentUtils
import molmed.utils.UppmaxConfig
import molmed.utils.UppmaxXMLConfiguration

/**
 * Align paired end reads to a reference using Tophat. By default cutadapt is not
 * run, but using the "--cutadapt" will add that step. Can also experimentally do
 * fusion search, however, this does not seem to be useful in the results
 * it generates.
 */

class AlignWithTophat extends QScript with UppmaxXMLConfiguration {

  qscript =>
  
  /****************************************************************************
   * Optional Parameters
   * **************************************************************************
   */
	
  /****************************************************************************
   * --------------------------------------------------------------------------
   * 		Arguments and Flags
   * --------------------------------------------------------------------------
   * **************************************************************************
   */

  @Argument(doc = "Do fussion search using tophat", fullName = "fusionSearch", shortName = "fs", required = false)
  var fusionSearch: Boolean = false
  
  @Argument(doc = "library type. Options: fr-unstranded (default), fr-firststrand, fr-secondstrand", fullName = "library_type", shortName = "lib", required = false)
  var libraryType: String = "fr-unstranded"

  @Argument(doc = "Run cutadapt", fullName = "cutadapt", shortName = "ca", required = false)
  var runCutadapt: Boolean = false
  
  @Argument(doc = "Number of threads tophat should use", fullName = "tophat_threads", shortName = "tt", required = false)
  var tophatThreads: Int = 1
  
  @Argument(doc = "Perform validation on the BAM files", fullName = "validation", shortName = "vs", required = false)
  var validation: Boolean = false
  
  /****************************************************************************
   * --------------------------------------------------------------------------
   * 		Input and output files/paths
   * --------------------------------------------------------------------------
   * **************************************************************************
   */
  
  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDirProcessedBAM: String = ""
  def getOutputDirBAM: String = if (outputDirProcessedBAM.isEmpty())  "raw_alignments/" else outputDirProcessedBAM + "/"
  
  @Argument(doc = "Annotations of known transcripts in GTF 2.2 or GFF 3 format.", fullName = "annotations", shortName = "a", required = false)
  var annotations: Option[File] = None
  
  /****************************************************************************
	 * --------------------------------------------------------------------------
	 *  	Path to applications
	 * --------------------------------------------------------------------------
	 ****************************************************************************
	 */
  @Input(doc = "The path to the binary of cutadapt", fullName = "path_to_cutadapt", shortName = "cutadapt", required = false)
  var cutadaptPath: File = new File("/local/programs/bin")  
 
  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = "samtools"
  
  @Input(doc = "The path to the perl script used correct for empty reads ", fullName = "path_sync_script", shortName = "sync", required = false)
  var syncPath: File = new File("resources/FixEmptyReads.pl")
    
  @Input(doc = "The path to the binary of tophat", fullName = "path_to_tophat", shortName = "tophat", required = false)
  var tophatPath: File = new File("tophat2")
  /**
   * The actual script
   */
  def script {
  	//-------------------------------------------------------------------------
    //                      Create output folders
    //-------------------------------------------------------------------------
    
    //Check for or create output dir for process BAM files
    val aligmentOutputDir: File = new File(getOutputDirBAM)    
    if(!aligmentOutputDir.exists()){
      aligmentOutputDir.mkdir()
    }
    
    //Import uppmxax-settings,samples and project info
    val uppmaxConfig = loadUppmaxConfigFromXML()    
    val sampleMap: Map[String, Seq[SampleAPI]] = setupReader.getSamples()    
    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)
    val tophatUtils = new TophatAligmentUtils(tophatPath, tophatThreads, projectName, uppmaxConfig)
    
    var cohortList: Seq[File] = Seq()
    var placeHolderList: Seq[File] = Seq()
     
    for ((sampleName, samples) <- sampleMap) {
      var counter = 1
      for (sample <- samples) {
        /**
		     * Make sure that if there are several instances of a sample
		     * they are processed separately with folder names:
		     * <original sample name>_<int>
		     */
        val sampleNameCounter = if(samples.size == 1) sampleName else sampleName + "_" + counter
        counter+=1
        
        //Align fastq file(s)
        val bamFile: File =
        if (runCutadapt)
        	tophatUtils.align(this,this.libraryType,this.annotations,aligmentOutputDir,sampleNameCounter,generalUtils.cutSamplesUsingCuteAdapt(this,this.cutadaptPath,sample,aligmentOutputDir,syncPath),this.fusionSearch)
        else
        	tophatUtils.align(this,this.libraryType,this.annotations,aligmentOutputDir,sampleNameCounter,sample,this.fusionSearch)
        
        cohortList :+= bamFile
      }
    }
    
    // output a BAM list with all the processed files
    val cohortFile = new File(aligmentOutputDir + "cohort.list")
    add(writeList(cohortList, cohortFile))

  }

  /**
   * Special list writer class which uses a place holder to make sure it waits for input.
   */
  case class writeList(@Input inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    override def jobRunnerJobName = projectName.get + "_bamList"
  }
}
