package molmed

import annotation.target.field

/**
 * Contains classes relating to:
 * 1) Commandline wrappers for various programs for use in the QScript
 * 2) Parameters and resource restrictions on uppmax
 * 3) Random stuff with no other home...
 *
 * @todo This should probably be better organized.
 */
package object utils {

  type Input = org.broadinstitute.sting.commandline.Input @field
  type Output = org.broadinstitute.sting.commandline.Output @field
  type Argument = org.broadinstitute.sting.commandline.Argument @field
  type ArgumentCollection = org.broadinstitute.sting.commandline.ArgumentCollection @field
  type Gather = org.broadinstitute.sting.commandline.Gather @field

}