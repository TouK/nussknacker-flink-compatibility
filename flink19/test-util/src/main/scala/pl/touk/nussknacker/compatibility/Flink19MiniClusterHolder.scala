package pl.touk.nussknacker.compatibility

import java.util.concurrent.CompletableFuture

import org.apache.flink.api.common.JobID
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration._
import org.apache.flink.runtime.client.JobStatusMessage
import org.apache.flink.runtime.executiongraph.AccessExecutionGraph
import org.apache.flink.runtime.jobgraph.{JobGraph, JobStatus}
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig

import scala.collection.JavaConverters._

class Flink19MiniClusterHolder(flinkMiniCluster: MiniClusterWithClientResource,
                               protected val userFlinkClusterConfig: Configuration,
                               protected val envConfig: AdditionalEnvironmentConfig) extends FlinkMiniClusterHolder {

  override def start(): Unit = {
    flinkMiniCluster.before()
    getClusterClient.setDetached(envConfig.detachedClient)
  }

  override def stop(): Unit =
    flinkMiniCluster.after()

  override def cancelJob(jobID: JobID): Unit =
    flinkMiniCluster.getClusterClient.cancel(jobID)

  override def submitJob(jobGraph: JobGraph): JobID =
    flinkMiniCluster.getClusterClient.submitJob(jobGraph, getClass.getClassLoader).getJobID

  override def listJobs(): List[JobStatusMessage] =
    flinkMiniCluster.getClusterClient.listJobs().get().asScala.toList

  override def runningJobs(): List[JobID] =
    listJobs().filter(_.getJobState == JobStatus.RUNNING).map(_.getJobId)

  override def getExecutionGraph(jobId: JobID): CompletableFuture[_ <: AccessExecutionGraph] =
    flinkMiniCluster.getMiniCluster.getExecutionGraph(jobId)

  def getClusterClient: ClusterClient[_] = flinkMiniCluster.getClusterClient

}

object Flink19MiniClusterHolder {

  def apply(userFlinkClusterConfig: Configuration, envConfig: AdditionalEnvironmentConfig = AdditionalEnvironmentConfig()): FlinkMiniClusterHolder = {
    userFlinkClusterConfig.setBoolean(ConfigConstants.LOCAL_START_WEBSERVER, false)
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    val resource = prepareMiniClusterResource(userFlinkClusterConfig)
    new Flink19MiniClusterHolder(resource, userFlinkClusterConfig, envConfig)
  }

  def addQueryableStateConfiguration(configuration: Configuration, proxyPortLow: Int, taskManagersCount: Int): Configuration = {
    val proxyPortHigh = proxyPortLow + taskManagersCount - 1
    configuration.setBoolean(QueryableStateOptions.ENABLE_QUERYABLE_STATE_PROXY_SERVER, true)
    configuration.setString(QueryableStateOptions.PROXY_PORT_RANGE, s"$proxyPortLow-$proxyPortHigh")
    configuration
  }

  def prepareMiniClusterResource(userFlinkClusterConfig: Configuration): MiniClusterWithClientResource = {
    val clusterConfig: MiniClusterResourceConfiguration = new MiniClusterResourceConfiguration.Builder()
      .setNumberTaskManagers(userFlinkClusterConfig.getInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, ConfigConstants.DEFAULT_LOCAL_NUMBER_TASK_MANAGER))
      .setNumberSlotsPerTaskManager(userFlinkClusterConfig.getInteger(TaskManagerOptions.NUM_TASK_SLOTS, TaskManagerOptions.NUM_TASK_SLOTS.defaultValue()))
      .setConfiguration(userFlinkClusterConfig)
      .build
    new MiniClusterWithClientResource(clusterConfig)
  }
}
