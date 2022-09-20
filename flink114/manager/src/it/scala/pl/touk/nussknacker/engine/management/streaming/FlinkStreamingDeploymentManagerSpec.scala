package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.management.CustomFlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.management.common.{CommonFlinkStreamingDeploymentManagerProvider, CommonFlinkStreamingDeploymentManagerSpec}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

class FlinkStreamingDeploymentManagerSpec extends CommonFlinkStreamingDeploymentManagerSpec {

  override protected def classPath: String =
    s"./flink114/model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flink114-model-assembly.jar"

  override protected def deploymentManagerProvider: CommonFlinkStreamingDeploymentManagerProvider =
    new CustomFlinkStreamingDeploymentManagerProvider()

  override protected def flinkEsp =
    s"flinkesp:1.14.5-scala_${ScalaMajorVersionConfig.scalaMajorVersion}"
}
