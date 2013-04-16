package molmed.queue.extensions.RNAQC

import org.broadinstitute.sting.commandline._
import java.io.File
import org.broadinstitute.sting.queue.function.CommandLineFunction

class RNASeQC extends CommandLineFunction {

    analysisName = "RNASeQC"

    @Input(doc = "Sample File: tab-delimited description of samples and their bams. \n With header: Sample ID    Bam File    Notes", shortName = "i", fullName = "input", required = true)
    var input: File = _

    @Input(doc = "Reference file in fasta format.", shortName = "r", fullName = "reference", required = true)
    var reference: File = _

    @Input(doc = "GTF File defining the transcripts (must end in .gtf)", shortName = "t", fullName = "transcripts", required = true)
    var transcripts: File = _

    @Input(doc = "Output directory", shortName = "o", fullName = "output_dir", required = true)
    var output: File = _

    @Argument(doc = "intervalFIle for rRNA loci (must end in .list). This is an alternative flag to the -BWArRNA flag.", shortName = "rRNA", fullName = "rRNA_targets", required = false)
    var rRNATargets: File = _
    
    val rRNATargetString = if(rRNATargets != null) " -rRNA " + rRNATargets else ""
    
    override def commandLine = "java -jar resources/RNA-SeQC_v1.1.7.jar " +
        " -s " + input +
        " -r " + reference +
        " -t " + transcripts +        
        " -o " + output +
        rRNATargetString
}