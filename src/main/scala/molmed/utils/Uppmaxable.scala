package molmed.utils

import org.broadinstitute.sting.commandline.Hidden
import org.broadinstitute.sting.commandline.Argument

trait Uppmaxable {

  @Hidden
  @Argument(doc = "Uppmax qos flag", fullName = "quality_of_service", shortName = "qos", required = false)
  var uppmaxQoSFlag: Option[String] = None

  @Hidden
  @Argument(doc = "Uppmax project id", fullName = "project_id", shortName = "upid", required = false)
  var projId: String = ""
}