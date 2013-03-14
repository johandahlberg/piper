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

import org.testng.annotations.Test
import se.uu.medsci.queue.SnpSeqBaseTest
import org.broadinstitute.sting.queue.pipeline._

/**
 * TODO
 * Implement cluster style testing as in AlignWithBwaSnpSeqPipelineTest
 */

class DataProcessingSnpSeqPipelineTest {
  
  val pathToScript = "-S src/main/scala/molmed/qscripts/DataProcessingPipeline.scala"
    
  val snpSeqBaseTest = new SnpSeqBaseTest()  
    
  @Test(timeOut=36000000)
  def testSimpleBAM {
    val projectName = "test1"
    val testOut = projectName + ".exampleBAM.clean.dedup.recal.bam"
    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "DataProcessingPipeline"
    spec.args = Array(
      pathToScript,
      " -R " + snpSeqBaseTest.publicTestDir + "exampleFASTA.fasta",
      " -i " + snpSeqBaseTest.publicTestDir + "exampleBAM.bam",
      " -D " + snpSeqBaseTest.publicTestDir + "exampleDBSNP.vcf",
      " -test ",
      "-startFromScratch ",
      " -p " + projectName).mkString
    spec.fileMD5s += testOut -> "bf4f4f79809837f2143efc033ed228c6"
    PipelineTest.executeTest(spec)
  }

  @Test(timeOut=36000000)
  def testBWAPEBAM {
    val projectName = "test2"
    val testOut = projectName + ".exampleBAM.clean.dedup.recal.bam"
    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "DataProcessingPipeline"
    spec.args = Array(
      pathToScript,
      " -R " + snpSeqBaseTest.publicTestDir + "exampleFASTA.fasta",
      " -i " + snpSeqBaseTest.publicTestDir + "exampleBAM.bam",
      " -D " + snpSeqBaseTest.publicTestDir + "exampleDBSNP.vcf",
      " -test ",
      " --realign ",
      " -bwa /usr/bin/bwa",
      " -bwape ",
      " -startFromScratch ",
      " -p " + projectName).mkString
    spec.fileMD5s += testOut -> "13ffa44c798825c70b5d185c6a79df4c"
    PipelineTest.executeTest(spec)
  }
  
  @Test
  def testBWAPEBAMWithRevert {
    val projectName = "test2"
    val testOut = projectName + ".exampleBAM.clean.dedup.recal.bam"
    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "DataProcessingPipeline"
    spec.args = Array(
      pathToScript,
      " -R " + snpSeqBaseTest.publicTestDir + "exampleFASTA.fasta",
      " -i " + snpSeqBaseTest.publicTestDir + "exampleBAM.bam",
      " -D " + snpSeqBaseTest.publicTestDir + "exampleDBSNP.vcf",
      " -test ",
      " --realign ",
      " -bwa /usr/bin/bwa",
      " -bwape ",
      " --revert ",
      "-startFromScratch ",
      " -p " + projectName).mkString
    spec.fileMD5s += testOut -> "7f7a0fd29fbaf88a5c82afe16de43c29"
    PipelineTest.executeTest(spec)
  }

}
