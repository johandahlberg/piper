package molmed.qscripts

/*
 * Copyright (c) 2011, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.sting.queue.pipeline._

/**
 * TODO
 * Implement cluster style testing as in AlignWithBwaSnpSeqPipelineTest
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
            " -i " + snpSeqBaseTest.publicTestDir + "exampleBAM.bam",
            " -startFromScratch ",
            " --project_id " + projectName).mkString
        spec.fileMD5s += sampleCumulativeCoverageCounts -> "ef78a6cdfd7629a0265740c7b7033f24"
        spec.fileMD5s += sampleCumulativeCoverageProportions -> "b5e5fab7044583b50329dae85608ee9d"
        spec.fileMD5s += sampleIntervalStatistics -> "d41d8cd98f00b204e9800998ecf8427e"
        spec.fileMD5s += sampleIntervalSummary -> "d41d8cd98f00b204e9800998ecf8427e"
        spec.fileMD5s += sampleStatistics -> "c312888f7767ebe1100ff4d7749d593a"
        spec.fileMD5s += sampleSummary -> "50cecb3e09dc226b3640c617ec9657b7"
        PipelineTest.executeTest(spec, run)
    }
}
