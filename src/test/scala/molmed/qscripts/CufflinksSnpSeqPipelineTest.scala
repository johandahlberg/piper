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

class CufflinksSnpSeqPipelineTest {

  val pathToScript = "-S src/main/scala/molmed/qscripts/Cufflinks.scala"

  val snpSeqBaseTest = SnpSeqBaseTest

  var run: Boolean = false

  @BeforeClass
  @Parameters(Array("runpipeline"))
  def init(runpipeline: Boolean): Unit = {
    this.run = runpipeline
  }

    @Test
    def testBasicCufflinks {
      val projectName = "test1"
  
      val transcripts = "cufflinks/Pairend_StrandSpecific_51mer_Human_hg19.with.rg/transcripts.gtf"
      val genesFpkmTracking = "cufflinks/Pairend_StrandSpecific_51mer_Human_hg19.with.rg/genes.fpkm_tracking"
  
      val spec = new PipelineTestSpec
      spec.jobRunners = Seq("Shell")
      spec.name = "BasicCufflinks"
      spec.args = Array(
        pathToScript,
        " -i " + snpSeqBaseTest.pathToRNAtestBam,
        " --reference " + snpSeqBaseTest.hg19,
        " --annotations " + snpSeqBaseTest.hg19annotations,
        " --path_to_cufflinks " + snpSeqBaseTest.pathToCufflinks,
        " -startFromScratch ").mkString
  
      spec.fileMD5s += transcripts -> "25d32dd6c9833687aa18b96383a0b088"
      spec.fileMD5s += genesFpkmTracking -> "cd9619944c297f98595cf5838466e8c7"
  
      PipelineTest.executeTest(spec, run)
    }

  // @TODO Excluding this from the workflow for now as it takes way to long to run.
  // plust that it seems experimental at best. Maybe this will be tested in the future.
  //  @Test
  //  def testFindNovelCufflinks {
  //    val projectName = "test1"
  //
  //    val transcripts = "cufflinks/Pairend_StrandSpecific_51mer_Human_hg19/transcripts.gtf"
  //    val genesFpkmTracking = "cufflinks/Pairend_StrandSpecific_51mer_Human_hg19/genes.fpkm_tracking"
  //
  //    val spec = new PipelineTestSpec
  //    spec.jobRunners = Seq("Shell")
  //    spec.name = "FindNovelCufflinks"
  //    spec.args = Array(
  //      pathToScript,
  //      " -i " + snpSeqBaseTest.pathToRNAtestBam,
  //      " --reference " + snpSeqBaseTest.hg19,
  //      " --annotations " + snpSeqBaseTest.hg19annotations,
  //      " --path_to_cufflinks " + snpSeqBaseTest.pathToCufflinks,
  //      " --findNovel ",
  //      " --merge ",
  //      " -startFromScratch ").mkString
  //
  //    spec.fileMD5s += transcripts -> "25d32dd6c9833687aa18b96383a0b088"
  //    spec.fileMD5s += genesFpkmTracking -> "cd9619944c297f98595cf5838466e8c7"
  //
  //    PipelineTest.executeTest(spec, run)
  //  }
}
