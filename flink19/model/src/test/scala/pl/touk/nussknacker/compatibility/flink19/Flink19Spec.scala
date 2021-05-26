package pl.touk.nussknacker.compatibility.flink19

import org.apache.flink.configuration.{ConfigConstants, Configuration, NettyShuffleEnvironmentOptions, TaskManagerOptions}
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration.addQueryableStatePortRanges
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder

trait Flink19Spec extends BeforeAndAfterAll {
  self: Suite =>

  var flinkMiniCluster: FlinkMiniClusterHolder = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val userFlinkClusterConfig = prepareFlinkConfiguration()

    val clusterConfig: MiniClusterResourceConfiguration = new MiniClusterResourceConfiguration.Builder()
      //Flink 1.9 change of configuration options name
      .setNumberTaskManagers(userFlinkClusterConfig.getInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, ConfigConstants.DEFAULT_LOCAL_NUMBER_JOB_MANAGER))
      .setNumberSlotsPerTaskManager(userFlinkClusterConfig.getInteger(TaskManagerOptions.NUM_TASK_SLOTS, TaskManagerOptions.NUM_TASK_SLOTS.defaultValue()))
      .setConfiguration(userFlinkClusterConfig)
      .build

    val resource = new MiniClusterWithClientResource(clusterConfig)
    flinkMiniCluster = new Flink19MiniClusterHolder(resource, userFlinkClusterConfig, prepareEnvConfig())
    flinkMiniCluster.start()
  }

  protected def prepareEnvConfig(): AdditionalEnvironmentConfig = {
    AdditionalEnvironmentConfig()
  }

  protected def prepareFlinkConfiguration(): Configuration = {
    val config = new Configuration
    config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, 2)
    config.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, 8)
    // to prevent OutOfMemoryError: Could not allocate enough memory segments for NetworkBufferPool on low memory env (like Travis)

    //Flink 1.9 - change of configuration options name
    config.setString(NettyShuffleEnvironmentOptions.NETWORK_BUFFERS_MEMORY_MIN, "16m")
    config.setString(NettyShuffleEnvironmentOptions.NETWORK_BUFFERS_MEMORY_MAX, "16m")
    addQueryableStatePortRanges(config)
  }

  override protected def afterAll(): Unit = {
    try {
      flinkMiniCluster.stop()
    } finally {
      super.afterAll()
    }
  }
}
