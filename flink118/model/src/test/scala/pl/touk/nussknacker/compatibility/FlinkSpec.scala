package pl.touk.nussknacker.compatibility

import org.apache.flink.configuration.{Configuration, CoreOptions}
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig
import pl.touk.nussknacker.engine.flink.test.{FlinkMiniClusterHolder, FlinkTestConfiguration}

trait FlinkSpec extends BeforeAndAfterAll {
  self: Suite =>

  var flinkMiniCluster: FlinkMiniClusterHolder = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val userFlinkClusterConfig = prepareFlinkConfiguration()
    userFlinkClusterConfig.set[java.lang.Boolean](CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
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
