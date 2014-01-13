package molmed.utils

/**
 * Container class to hold the parameters for running on uppmax.
 */
case class UppmaxConfig(
  projId: String = UppmaxConfig.defaultProjectId,
  uppmaxQoSFlag: Option[String] = UppmaxConfig.defaultUppmaxQoSFlag,
  testMode: Boolean = false)

/**
 * Quick and dirty solution for holding default parameters for the uppmax config in
 * one place.
 */
object UppmaxConfig {
  val defaultProjectId = ""
  val defaultUppmaxQoSFlag: Option[String] = None
}