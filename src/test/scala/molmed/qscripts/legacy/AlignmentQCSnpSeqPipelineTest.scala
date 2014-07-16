package molmed.qscripts.legacy

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.gatk.queue.pipeline._

/**
 * TODO
 * Implement cluster style tegatk as in AlignWithBwaSnpSeqPipelineTest
 */

class AlignmentQCSnpSeqPipelineTest {

    val pathToScript = "-S src/main/scala/molmed/qscripts/AlignmentQC.scala"

    val snpSeqBaseTest = SnpSeqBaseTest

    var run: Boolean = false

    @BeforeClass
    @Parameters(Array("runpipeline"))
    def init(runpipeline: Boolean): Unit = {
        this.run = runpipeline
    }

    @Test
    def testBasicAlignmentQC {
        val projectName = "test1"
        
        val sampleCumulativeCoverageCounts = "exampleBAM.sample_cumulative_coverage_counts"
        val sampleCumulativeCoverageProportions = "exampleBAM.sample_cumulative_coverage_proportions"
        val sampleIntervalStatistics = "exampleBAM.sample_interval_statistics"
        val sampleIntervalSummary = "exampleBAM.sample_interval_summary"  
        val sampleStatistics = "exampleBAM.sample_statistics"
        val sampleSummary = "exampleBAM.sample_summary"
            
        val spec = new PipelineTestSpec
        spec.jobRunners = Seq("Shell")
        spec.name = "AlignmentQCPipeline"
        spec.args = Array(
            pathToScript,
            " -R " + snpSeqBaseTest.publicTestDir + "exampleFASTA.fasta",
            " --xml_input " + snpSeqBaseTest.pathSetupFile,
            " -i " + snpSeqBaseTest.publicTestDir + "exampleBAM.bam",
            " -startFromScratch ",
            " --project_id " + projectName).mkString
        spec.fileMD5s += sampleCumulativeCoverageCounts -> "ef78a6cdfd7629a0265740c7b7033f24"
        spec.fileMD5s += sampleCumulativeCoverageProportions -> "b5e5fab7044583b50329dae85608ee9d"
        spec.fileMD5s += sampleIntervalStatistics -> "d41d8cd98f00b204e9800998ecf8427e"
        spec.fileMD5s += sampleIntervalSummary -> "d41d8cd98f00b204e9800998ecf8427e"
        spec.fileMD5s += sampleStatistics -> "c312888f7767ebe1100ff4d7749d593a"
        spec.fileMD5s += sampleSummary -> "50cecb3e09dc226b3640c617ec9657b7"
        
        spec.run = run        
        PipelineTest.executeTest(spec)
    }
}
