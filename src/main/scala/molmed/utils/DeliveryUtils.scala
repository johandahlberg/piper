package molmed.utils

import java.io.File
import org.broadinstitute.sting.queue.function.InProcessFunction
import molmed.queue.setup.SampleAPI

object DeliveryUtils {
  case class SetupDeliveryStructure(
    @Argument var samples: Seq[SampleAPI],
    @Input var processedBamFiles: Seq[File],
    @Input var qualityControlFiles: Seq[File],
    @Input var variantCallFiles: Seq[File],
    @Output var deliveryDirectory: File)
      extends InProcessFunction {

    def createHardLinksForSamples(samples: Seq[SampleAPI], outputDir: File) {
      for ((sampleName, samplesWithThatName) <- samples.groupBy(x => x.getSampleName)) {

        val sampleDir = new File(outputDir + "/" + sampleName)
        sampleDir.mkdirs()

        val groupedByFlowcell = samplesWithThatName.groupBy(x => x.getReadGroupInformation.platformUnitId)

        for ((flowcell, samples) <- groupedByFlowcell) {

          val flowcellDir = new File(sampleDir + "/" + flowcell)
          flowcellDir.mkdirs()

          samples.map(sample => {
            createHardLinksForFiles(sample.getFastqs.getFiles, flowcellDir)
          })
        }
      }
    }

    def createHardLinksForFiles(files: Seq[File], outputDir: File, withWildCard: Boolean = false) {
      for (file <- files) {
        val outputFile = new File(outputDir + "/" + file.getName())
        val exitCode = GeneralUtils.linkProcess(file, outputFile, withWildCard)
      }
    }

    def run() {

      if (!deliveryDirectory.exists())
        deliveryDirectory.mkdirs()

      val rawDir = new File(deliveryDirectory + "/raw")
      rawDir.mkdirs()

      val alignedDir = new File(deliveryDirectory + "/aligned")
      alignedDir.mkdirs()

      val qualityControlOutputDir = new File(deliveryDirectory + "/quality_control")
      qualityControlOutputDir.mkdirs()

      val variantCallsDir = new File(deliveryDirectory + "/variants_calls")
      variantCallsDir.mkdirs()

      // Since the quality control files are really log files we need
      // to remove the log ending to get the correct file names.
      val realQualityControlDirs = qualityControlFiles.map(
        file => new File(file.getPath().replace(".log", "")))

      createHardLinksForSamples(samples, rawDir)
      createHardLinksForFiles(processedBamFiles, alignedDir)
      createHardLinksForFiles(realQualityControlDirs, qualityControlOutputDir, withWildCard = true)
      createHardLinksForFiles(variantCallFiles, variantCallsDir)
    }
  }

}