package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.management.common.CommonFlinkStreamingDeploymentManagerProvider

class CustomFlinkStreamingDeploymentManagerProvider extends CommonFlinkStreamingDeploymentManagerProvider {
  override def name: String = "flink111Streaming"
}
