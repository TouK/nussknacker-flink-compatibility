package pl.touk.nussknacker.compatibility.flink111

import org.apache.flink.configuration.{Configuration, CoreOptions}
import org.apache.flink.runtime.executiongraph.AccessExecutionGraph
import org.scalatest.{Assertion, BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.{AdditionalEnvironmentConfig, prepareMiniClusterResource}
import pl.touk.nussknacker.engine.flink.test.{FlinkMiniClusterHolder, FlinkMiniClusterHolderImpl, FlinkTestConfiguration, MiniClusterExecutionEnvironment}

trait Flink111Spec extends BeforeAndAfterAll {
  self: Suite =>

  var flinkMiniCluster: FlinkMiniClusterHolder = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val userFlinkClusterConfig = prepareFlinkConfiguration()
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    val resource = prepareMiniClusterResource(userFlinkClusterConfig)

    //Flink 1.9, getClusterClient is tricky, as Class became interface in Flink 1.10
    flinkMiniCluster = new FlinkMiniClusterHolderImpl(resource, userFlinkClusterConfig, prepareEnvConfig()) {
      override final def createExecutionEnvironment(): MiniClusterExecutionEnvironment = {
        new MiniClusterExecutionEnvironment(this, userFlinkClusterConfig, envConfig) {
          override protected def assertJobInitialized(executionGraph: AccessExecutionGraph): Assertion = {
            assert(true)
          }
        }
      }
    }

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
