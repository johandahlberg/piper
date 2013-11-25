package molmed.utils

case class UppmaxConfig(
    projId: String = UppmaxConfig.defaultProjectId,
    uppmaxQoSFlag: Option[String] = UppmaxConfig.defaultUppmaxQoSFlag,
    clusterName: Option[String] = UppmaxConfig.defaultClusterName
    )

object UppmaxConfig {
  val defaultProjectId = ""
  val defaultUppmaxQoSFlag: Option[String] = None
  val defaultClusterName = Some("milou")  
}