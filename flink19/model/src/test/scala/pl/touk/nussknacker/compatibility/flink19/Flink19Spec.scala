package pl.touk.nussknacker.compatibility.flink19

import org.apache.flink.api.common.{JobExecutionResult, JobID}
import org.apache.flink.configuration.{ConfigConstants, Configuration, MemorySize, NettyShuffleEnvironmentOptions, TaskManagerOptions}
import org.apache.flink.runtime.jobgraph.JobGraph
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.apache.flink.util.OptionalFailure
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration.addQueryableStatePortRanges
import pl.touk.nussknacker.engine.flink.test.{FlinkMiniClusterHolder, FlinkMiniClusterHolderImpl, MiniClusterExecutionEnvironment}

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

    //Flink 1.9, getClusterClient is tricky, as Class became interface in Flink 1.10
    flinkMiniCluster = new FlinkMiniClusterHolderImpl(resource, userFlinkClusterConfig, prepareEnvConfig()) {
      override final def createExecutionEnvironment(): MiniClusterExecutionEnvironment = {
        val flinkMiniClusterHolder = this
        new MiniClusterExecutionEnvironment(this, userFlinkClusterConfig, envConfig) {
          override def execute(streamGraph: StreamGraph): JobExecutionResult = {
            val jobGraph: JobGraph = streamGraph.getJobGraph
            logger.debug("Running job on local embedded Flink flinkMiniCluster cluster")

            jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)

            // Is passed classloader is ok?
            val client = flinkMiniClusterHolder.getClusterClient
            client.setDetached(true)
            val submissionResult = client.submitJob(jobGraph, getClass.getClassLoader)

            new JobExecutionResult(submissionResult.getJobID, 0, new java.util.HashMap[String, OptionalFailure[AnyRef]]())
          }

          override def cancel(jobId: JobID): Unit = {
            flinkMiniClusterHolder.getClusterClient.cancel(jobId)
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
