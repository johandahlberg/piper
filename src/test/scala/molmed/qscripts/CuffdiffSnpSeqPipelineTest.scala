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

class CuffdiffSnpSeqPipelineTest {

  val pathToScript = "-S src/main/scala/molmed/qscripts/Cuffdiff.scala"

  val snpSeqBaseTest = SnpSeqBaseTest

  var run: Boolean = false

  @BeforeClass
  @Parameters(Array("runpipeline"))
  def init(runpipeline: Boolean): Unit = {
    this.run = runpipeline
  }

  @Test
  def testBasicCuffdiff {
    val projectName = "test1"

    val geneExp = "gene_exp.diff"
    val isoforms = "isoform_exp.diff"

    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "BasicCuffdiff"
    spec.args = Array(
      pathToScript,
      " -i " + snpSeqBaseTest.pathToCuffdiffCohortFile,
      " --reference " + snpSeqBaseTest.hg19,
      " --annotations " + snpSeqBaseTest.hg19annotations,
      " --path_to_cuffdiff " + snpSeqBaseTest.pathToCuffdiff,
      " --library_type " + " fr-secondstrand ",
      " -startFromScratch ").mkString

    spec.fileMD5s += geneExp -> "7ec98841c806d6e56d67693f1b799e7f"
    spec.fileMD5s += isoforms -> "a954b8ab4293a587a66351071a9f72a7"

    PipelineTest.executeTest(spec, run)
  }
  
}
