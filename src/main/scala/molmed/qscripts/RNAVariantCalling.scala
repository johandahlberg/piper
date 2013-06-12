package molmed.qscripts

package molmed.qscripts

import java.io.FileNotFoundException
import scala.collection.JavaConversions._
import org.broadinstitute.sting.commandline.Hidden
import org.broadinstitute.sting.gatk.walkers.indels.IndelRealigner.ConsensusDeterminationModel
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.function.ListWriterFunction
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.utils.baq.BAQ.CalculationMode
import net.sf.picard.reference.IndexedFastaSequenceFile
import net.sf.samtools.SAMFileHeader.SortOrder
import net.sf.samtools.SAMFileReader
import molmed.qscripts.indelCall

class DataProcessingPipeline extends QScript {
    qscript =>

    /**
     * **************************************************************************
     * Required Parameters
     * **************************************************************************
     */

    @Input(doc = "input BAM file - or list of BAM files", fullName = "input", shortName = "i", required = true)
    var input: File = _

    @Input(doc = "Reference fasta file", fullName = "reference", shortName = "R", required = true)
    var reference: File = _

    @Input(doc = "dbsnp ROD to use (must be in VCF format)", fullName = "dbsnp", shortName = "D", required = true)
    var dbSNP: Seq[File] = Seq()

    /**
     * **************************************************************************
     * Optional Parameters
     * **************************************************************************
     */

    @Input(doc = "extra VCF files to use as reference indels for Indel Realignment", fullName = "extra_indels", shortName = "indels", required = false)
    var indels: Seq[File] = Seq()

    @Argument(doc = "the project name determines the final output (BAM file) base name. Example NA12878 yields NA12878.processed.bam", fullName = "project", shortName = "p", required = false)
    var projectName: String = "project"

    @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
    var outputDir: String = ""

    @Argument(doc = "the -L interval string to be used by GATK - output bams at interval only", fullName = "gatk_interval_string", shortName = "L", required = false)
    var intervalString: String = ""

    @Input(doc = "an intervals file to be used by GATK - output bams at intervals only", fullName = "gatk_interval_file", shortName = "intervals", required = false)
    var intervals: File = _

    @Argument(doc = "Cleaning model: KNOWNS_ONLY, USE_READS or USE_SW", fullName = "clean_model", shortName = "cm", required = false)
    var cleaningModel: String = "USE_READS"

    @Argument(doc = "Perform validation on the BAM files", fullName = "validation", shortName = "vs", required = false)
    var validation: Boolean = false

    @Argument(doc = "Number of threads to use in thread enabled walkers. Default: 1", fullName = "nbr_of_threads", shortName = "nt", required = false)
    var nbrOfThreads: Int = 1

    @Argument(shortName = "mbq", doc = "The minimum Phred-Scaled quality score threshold to be considered a good base.", required = false)
    var minimumBaseQuality: Int = -1

    @Argument(shortName = "deletions", doc = "Maximum deletion fraction allowed at a site to call a genotype.", required = false)
    var deletions: Double = -1

    @Argument(doc = "Downsample fraction. [0.0 - 1.0]", fullName = "downsample_to_fraction", shortName = "dtf", required = false)
    var downsampleFraction: Double = -1

    /**
     * **************************************************************************
     * Hidden Parameters
     * **************************************************************************
     */
    @Hidden
    @Argument(doc = "How many ways to scatter/gather", fullName = "scatter_gather", shortName = "sg", required = false)
    var nContigs: Int = -1

    @Hidden
    @Argument(doc = "Define the default platform for Count Covariates -- useful for techdev purposes only.", fullName = "default_platform", shortName = "dp", required = false)
    var defaultPlatform: String = ""

    @Hidden
    @Argument(doc = "Run the pipeline in test mode only", fullName = "test_mode", shortName = "test", required = false)
    var testMode: Boolean = false

    /**
     * **************************************************************************
     * Global Variables
     * **************************************************************************
     */

    val queueLogDir: String = ".qlog/" // Gracefully hide Queue's output

    var cleanModelEnum: ConsensusDeterminationModel = ConsensusDeterminationModel.USE_READS

    /**
     * **************************************************************************
     * Helper classes and methods
     * **************************************************************************
     */

    def getIndelCleaningModel: ConsensusDeterminationModel = {
        if (cleaningModel == "KNOWNS_ONLY")
            ConsensusDeterminationModel.KNOWNS_ONLY
        else if (cleaningModel == "USE_SW")
            ConsensusDeterminationModel.USE_SW
        else
            ConsensusDeterminationModel.USE_READS
    }

    /**
     * **************************************************************************
     * Main script
     * **************************************************************************
     */

    def script() {
        // final output list of processed bam files
        var cohortList: Seq[File] = Seq()

        // sets the model for the Indel Realigner
        cleanModelEnum = getIndelCleaningModel

        // keep a record of the number of contigs in the first bam file in the list
        val bams = QScriptUtils.createSeqFromFile(input)

        // Scatter gatter to 23 or the number of contigs, depending on which is the smallest.
        if (nContigs < 0) {
            nContigs = math.min(QScriptUtils.getNumberOfContigs(bams(0)), 23)
        }

        // put each sample through the pipeline
        for (file <- bams) {

            // BAM files generated by the pipeline      
            val bam = if (outputDir.isEmpty())
                new File(qscript.projectName + "." + file.getName())
            else
                new File(outputDir + qscript.projectName + "." + file.getName)

            val cleanedBam = swapExt(bam, ".bam", ".clean.bam")
            val dedupedBam = swapExt(bam, ".bam", ".clean.dedup.bam")
            val recalBam = swapExt(bam, ".bam", ".clean.dedup.recal.bam")

            val candidateSnps = swapExt(bam, ".bam", ".candidate.snp.vcf")
            val candidateIndels = swapExt(bam, ".bam", ".candidate.indels.vcf")

            // Accessory files
            val targetIntervals = if (cleaningModel == ConsensusDeterminationModel.KNOWNS_ONLY) { globalIntervals } else { swapExt(bam, ".bam", ".intervals") }
            val metricsFile = swapExt(bam, ".bam", ".metrics")
            val preRecalFile = swapExt(bam, ".bam", ".pre_recal.table")
            val postRecalFile = swapExt(bam, ".bam", ".post_recal.table")
            val preOutPath = swapExt(bam, ".bam", ".pre")
            val postOutPath = swapExt(bam, ".bam", ".post")
            val preValidateLog = swapExt(bam, ".bam", ".pre.validation")
            val postValidateLog = swapExt(bam, ".bam", ".post.validation")

            /**
             * Start of actually adding the jobs.
             */
            // Deduplicate
            add(dedup(bam, dedupedBam, metricsFile))

            // Recalibrate
            add(cov(dedupedBam, preRecalFile),
                recal(dedupedBam, preRecalFile, recalBam),
                cov(recalBam, postRecalFile))

            // SNP and INDEL Calls
           
            //@TODO Continue here.
                
            add(snpCall(recalBam))
            add(indelCall(recalBam))

            // Take regions from previous step
            add(clean(Seq(bam), targetIntervals, cleanedBam))
            
            // Call snps/indels again (possibly only in previously identifed regions)
            
            // Variant effect predictor - get all variants which change a aa
            
            // Annotate all snps from the previous step
            
            

            cohortList :+= recalBam
        }

        // output a BAM list with all the processed per sample files
        val cohortFile = new File(qscript.outputDir + qscript.projectName + ".cohort.list")
        add(writeList(cohortList, cohortFile))
    }

    // Override the normal swapExt metod by adding the outputDir to the file path by default if it is defined.
    override def swapExt(file: File, oldExtension: String, newExtension: String) = {
        if (outputDir.isEmpty())
            new File(file.getName.stripSuffix(oldExtension) + newExtension)
        else
            swapExt(outputDir, file, oldExtension, newExtension);
    }

    /**
     * **************************************************************************
     * Classes (GATK Walkers)
     * **************************************************************************
     */

    // General arguments to non-GATK tools
    trait ExternalCommonArgs extends CommandLineFunction {
        this.memoryLimit = 24
        this.isIntermediate = true
    }

    // General arguments to GATK walkers
    trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {
        this.reference_sequence = qscript.reference
    }

    trait SAMargs extends PicardBamFunction with ExternalCommonArgs {
        this.maxRecordsInRam = 100000
    }

    def bai(bam: File) = new File(bam + ".bai")

    // 1.) Unified Genotyper Base
    class GenotyperBase(bam: Seq[File], tIntervals: Seq[File]) extends UnifiedGenotyper with CommandLineGATKArgs {

        if (downsampleFraction != -1)
            this.downsample_to_fraction = downsampleFraction

        this.reference_sequence = reference
        this.intervals = tIntervals
        this.scatterCount = nContigs
        this.nt = nbrOfThreads
        this.stand_call_conf =  50.0
        this.stand_emit_conf =  10.0 
        this.input_file = bam
        this.D = new File(t.dbsnpFile)
    }

    // Call SNPs with UG
    case class snpCall(bam: Seq[File], tIntervals: Seq[File], vcf: File) extends GenotyperBase(bam, tIntervals) {
        if (minimumBaseQuality >= 0)
            this.min_base_quality_score = minimumBaseQuality
        if (qscript.deletions >= 0)
            this.max_deletion_fraction = qscript.deletions

        this.out = vcf
        this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.SNP
        this.baq = org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY
        this.analysisName = "UG_snp"
        this.jobName = "UG_snp"
    }

    // Call Indels with UG
    case class indelCall(bam: Seq[File], tIntervals: Seq[File], vcf: File) extends GenotyperBase(bam, tIntervals) {
        this.out = vcf
        this.glm = org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.INDEL
        this.baq = org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF
        this.analysisName = "UG_Indel"
        this.jobName = "UG_Indel"
    }

    case class target(inBams: Seq[File], outIntervals: File) extends RealignerTargetCreator with CommandLineGATKArgs {

        this.num_threads = nbrOfThreads

        if (cleanModelEnum != ConsensusDeterminationModel.KNOWNS_ONLY)
            this.input_file = inBams
        this.out = outIntervals
        this.mismatchFraction = 0.0
        this.known ++= qscript.dbSNP
        if (indels != null)
            this.known ++= qscript.indels
        this.scatterCount = nContigs
        this.analysisName = queueLogDir + outIntervals + ".target"
        this.jobName = queueLogDir + outIntervals + ".target"
    }

    case class clean(inBams: Seq[File], tIntervals: File, outBam: File) extends IndelRealigner with CommandLineGATKArgs {

        this.input_file = inBams
        this.targetIntervals = tIntervals
        this.out = outBam
        this.known ++= qscript.dbSNP
        if (qscript.indels != null)
            this.known ++= qscript.indels
        this.consensusDeterminationModel = cleanModelEnum
        this.compress = 0
        this.noPGTag = qscript.testMode;
        this.scatterCount = nContigs
        this.analysisName = queueLogDir + outBam + ".clean"
        this.jobName = queueLogDir + outBam + ".clean"
    }

    case class cov(inBam: File, outRecalFile: File) extends BaseRecalibrator with CommandLineGATKArgs {

        this.num_cpu_threads_per_data_thread = nbrOfThreads

        this.knownSites ++= qscript.dbSNP
        this.covariate ++= Seq("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "ContextCovariate")
        this.input_file :+= inBam
        this.disable_indel_quals = false
        this.out = outRecalFile
        if (!defaultPlatform.isEmpty) this.default_platform = defaultPlatform
        if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
        else if (qscript.intervals != null) this.intervals :+= qscript.intervals

        this.scatterCount = nContigs
        this.analysisName = queueLogDir + outRecalFile + ".covariates"
        this.jobName = queueLogDir + outRecalFile + ".covariates"
    }

    case class recal(inBam: File, inRecalFile: File, outBam: File) extends PrintReads with CommandLineGATKArgs {

        this.input_file :+= inBam

        // TODO According to this thread: 
        // http://gatkforums.broadinstitute.org/discussion/2267/baq-tag-error#latest
        // There is a bug in the GATK which means that the baq calculations should not
        // be done in this step, but rather in the unified genotyper.
        this.BQSR = inRecalFile
        //this.baq = CalculationMode.CALCULATE_AS_NECESSARY
        this.out = outBam
        this.knownSites ++= qscript.dbSNP
        if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
        else if (qscript.intervals != null) this.intervals :+= qscript.intervals
        this.scatterCount = nContigs
        this.num_cpu_threads_per_data_thread = nbrOfThreads
        this.isIntermediate = false
        this.analysisName = queueLogDir + outBam + ".recalibration"
        this.jobName = queueLogDir + outBam + ".recalibration"
    }

    /**
     * **************************************************************************
     * Classes (non-GATK programs)
     * **************************************************************************
     */

    case class dedup(inBam: File, outBam: File, metricsFile: File) extends MarkDuplicates with ExternalCommonArgs {

        this.input :+= inBam
        this.output = outBam
        this.metrics = metricsFile
        this.memoryLimit = 16
        this.analysisName = queueLogDir + outBam + ".dedup"
        this.jobName = queueLogDir + outBam + ".dedup"
    }

    case class sortSam(inSam: File, outBam: File, sortOrderP: SortOrder) extends SortSam with ExternalCommonArgs {
        this.input :+= inSam
        this.output = outBam
        this.sortOrder = sortOrderP
        this.analysisName = queueLogDir + outBam + ".sortSam"
        this.jobName = queueLogDir + outBam + ".sortSam"
    }

    case class writeList(inBams: Seq[File], outBamList: File) extends ListWriterFunction {
        this.inputFiles = inBams
        this.listFile = outBamList
        this.analysisName = queueLogDir + outBamList + ".bamList"
        this.jobName = queueLogDir + outBamList + ".bamList"
    }
}
