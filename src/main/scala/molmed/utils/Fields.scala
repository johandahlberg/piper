package molmed.utils

import annotation.target.field

/**
 * Add this trait to your class to get all the fields setup correctly
 */
trait Fields {
  type Input = org.broadinstitute.gatk.utils.commandline.Input @field
  type Output = org.broadinstitute.gatk.utils.commandline.Output @field
  type Argument = org.broadinstitute.gatk.utils.commandline.Argument @field
  type ArgumentCollection = org.broadinstitute.gatk.utils.commandline.ArgumentCollection @field
  type Gather = org.broadinstitute.gatk.utils.commandline.Gather @field
  type Hidden = org.broadinstitute.gatk.utils.commandline.Hidden @field
}
