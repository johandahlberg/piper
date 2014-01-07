package molmed.queue.setup

/**
 * Auxilary case class for storing read group information
 */

case class ReadGroupInformation(sampleName: String, readGroupId: String, sequencingCenter: String,
    readLibrary: String, platform: String, platformUnitId: String,
    readsPassFilter: Option[Int] = None) {

    def parseToTophatApprovedString(): String = {

        /**
         * Format specification from the tophat manual.
         *
         * SAM Header Options (for embedding sequencing run metadata in output):
         * --rg-id                        <string>    (read group ID)
         * --rg-sample                    <string>    (sample ID)
         * --rg-library                   <string>    (library ID)
         * --rg-description               <string>    (descriptive string, no tabs allowed)
         * --rg-platform-unit             <string>    (e.g Illumina lane ID)
         * --rg-center                    <string>    (sequencing center name)
         * --rg-date                      <string>    (ISO 8601 date of the sequencing run)
         * --rg-platform                  <string>    (Sequencing platform descriptor)
         *
         */

        " --rg-id " + this.readGroupId + " --rg-sample " + this.sampleName + " --rg-library " +
            this.readLibrary + " --rg-platform-unit " + this.platformUnitId +
            " --rg-center " + this.sequencingCenter.replaceAll("\\s*", "") + " --rg-platform " + this.platform
    }

    def parseToBwaApprovedString(): String = {

        // The form which bwa wants, according to their manual is: @RG\tID:foo\tSM:bar
        val readGroupHeader: String = "\"" + """@RG\tID:""" + this.readGroupId + """\\tSM:""" + this.sampleName + """\\tCN:""" + this.sequencingCenter + """\\tLB:""" + this.readLibrary +
            """\\tPL:""" + this.platform + """\\tPU:""" + this.platformUnitId + "\""

        return readGroupHeader
    }

}