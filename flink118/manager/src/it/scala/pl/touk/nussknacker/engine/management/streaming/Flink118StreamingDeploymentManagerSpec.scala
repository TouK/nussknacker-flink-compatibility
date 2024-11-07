package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.management.common.CommonFlinkStreamingDeploymentManagerSpec
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

class Flink118StreamingDeploymentManagerSpec extends CommonFlinkStreamingDeploymentManagerSpec {
  override protected def classPath: String =
    s"./flink118/model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/nussknacker-flink-compatibility-1-18-model-assembly.jar"

  override protected def deploymentManagerProvider: FlinkStreamingDeploymentManagerProvider =
    new FlinkStreamingDeploymentManagerProvider()

  override protected val flinkVersion: String = "1.18.1"
}
