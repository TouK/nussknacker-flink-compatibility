package pl.touk.nussknacker.compatibility


import java.util.concurrent.CompletableFuture

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.JobID
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration.{ConfigConstants, Configuration, CoreOptions, TaskManagerOptions}
import org.apache.flink.runtime.executiongraph.AccessExecutionGraph
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.test.util.TestBaseUtils.CodebaseType
import org.apache.flink.test.util.{MiniClusterResource, MiniClusterResourceConfiguration}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig

object Flink16MiniClusterHolder {


  def apply(userFlinkClusterConfig: Configuration): FlinkMiniClusterHolder = apply(userFlinkClusterConfig, AdditionalEnvironmentConfig())

  def apply(userFlinkClusterConfig: Configuration, envConfig: AdditionalEnvironmentConfig): FlinkMiniClusterHolder = {
    userFlinkClusterConfig.setBoolean(ConfigConstants.LOCAL_START_WEBSERVER, false)
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    val resource = prepareMiniClusterResource(userFlinkClusterConfig)
    new Flink16MiniClusterHolder(resource, userFlinkClusterConfig, envConfig)
  }

  // Remove @silent after upgrade to silencer 1.7
  @silent
  @SuppressWarnings(Array("deprecatation"))
  def prepareMiniClusterResource(userFlinkClusterConfig: Configuration): MiniClusterResource = {
    val clusterConfig = new MiniClusterResourceConfiguration.Builder()
      .setNumberTaskManagers(userFlinkClusterConfig.getInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, ConfigConstants.DEFAULT_LOCAL_NUMBER_TASK_MANAGER))
      .setNumberSlotsPerTaskManager(userFlinkClusterConfig.getInteger(TaskManagerOptions.NUM_TASK_SLOTS, TaskManagerOptions.NUM_TASK_SLOTS.defaultValue()))
      .setConfiguration(userFlinkClusterConfig)
      .setCodebaseType(CodebaseType.NEW)
      .build
    //@see MiniClusterResource.before()...
    System.setProperty("codebase", "new")
    new MiniClusterResource(clusterConfig)
  }


}


class Flink16MiniClusterHolder(miniClusterResource: MiniClusterResource,
                               protected val userFlinkClusterConfig: Configuration,
                               protected val envConfig: AdditionalEnvironmentConfig
                              ) extends FlinkMiniClusterHolder {

  private def miniCluster: MiniCluster = {
    val miniClusterField = classOf[MiniClusterResource].getDeclaredField("jobExecutorService")
    miniClusterField.setAccessible(true)
    miniClusterField.get(miniClusterResource).asInstanceOf[MiniCluster]
  }

  override def start(): Unit = {
    miniClusterResource.before()
    getClusterClient.setDetached(envConfig.detachedClient)
  }

  override def stop(): Unit = {
    miniClusterResource.after()
  }

  override def getClusterClient: ClusterClient[_] = {
    miniClusterResource.getClusterClient
  }

  override def getExecutionGraph(jobId: JobID): CompletableFuture[_ <: AccessExecutionGraph] = {
    miniCluster.getExecutionGraph(jobId)
  }

}

