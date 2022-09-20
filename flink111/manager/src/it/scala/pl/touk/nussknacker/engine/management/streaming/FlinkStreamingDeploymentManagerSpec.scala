package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.management.CustomFlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.management.common.{CommonFlinkStreamingDeploymentManagerProvider, CommonFlinkStreamingDeploymentManagerSpec}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

class FlinkStreamingDeploymentManagerSpec extends CommonFlinkStreamingDeploymentManagerSpec {

  override protected def classPath: String =
    s"./flink111/model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flink111-model-assembly.jar"

  override protected def deploymentManagerProvider: CommonFlinkStreamingDeploymentManagerProvider =
    new CustomFlinkStreamingDeploymentManagerProvider()

  override protected def flinkEsp =
    s"flinkesp:1.11.2-scala_${ScalaMajorVersionConfig.scalaMajorVersion}"
}
