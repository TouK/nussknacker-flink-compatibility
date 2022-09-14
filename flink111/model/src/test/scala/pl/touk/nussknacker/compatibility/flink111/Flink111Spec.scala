package pl.touk.nussknacker.compatibility.flink111

import org.apache.flink.configuration.{Configuration, CoreOptions}
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.{AdditionalEnvironmentConfig, prepareMiniClusterResource}
import pl.touk.nussknacker.engine.flink.test.{FlinkMiniClusterHolder, FlinkMiniClusterHolderImpl, FlinkTestConfiguration, MiniClusterExecutionEnvironment}

trait Flink111Spec extends BeforeAndAfterAll {
  self: Suite =>

  var flinkMiniCluster: FlinkMiniClusterHolder = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val userFlinkClusterConfig = prepareFlinkConfiguration()
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    flinkMiniCluster = FlinkMiniClusterHolder(userFlinkClusterConfig, prepareEnvConfig())
    flinkMiniCluster.start()
  }

  protected def prepareEnvConfig(): AdditionalEnvironmentConfig = {
    AdditionalEnvironmentConfig()
  }

  protected def prepareFlinkConfiguration(): Configuration = {
    FlinkTestConfiguration.configuration()
  }

  override protected def afterAll(): Unit = {
    try {
      flinkMiniCluster.stop()
    } finally {
      super.afterAll()
    }
  }
}
