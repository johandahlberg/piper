package molmed

import java.io.File

/**
 * The QScripts are the hearth of Piper. They are used to define how the pipeline are run.
 * For a guide to writing QScripts see: http://www.broadinstitute.org/gatk/guide/topic?name=queue
 * @todo Add more documentation about the uppmax/piper specific parts for the qscripts
 *
 */
package object qscripts {

  /**
   * Implicitly convert any File to Option File, as necessary.
   */
  implicit def file2Option(file: File) = if (file == null) None else Some(file)

}