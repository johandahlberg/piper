package molmed

import annotation.target.field

/**
 * This package contains classes relating to configuring Piper.
 * E.g. to setup Uppmax related properies, but also specifying
 * paths to resources and programs. 
 */
package object config {

  type Input = org.broadinstitute.sting.commandline.Input @field
  type Output = org.broadinstitute.sting.commandline.Output @field
  type Argument = org.broadinstitute.sting.commandline.Argument @field
  type ArgumentCollection = org.broadinstitute.sting.commandline.ArgumentCollection @field
  type Gather = org.broadinstitute.sting.commandline.Gather @field

}