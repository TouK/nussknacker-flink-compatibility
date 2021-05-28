package pl.touk.nussknacker.compatibility.flink19

import org.apache.flink.api.common.JobID
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.client.JobStatusMessage
import org.apache.flink.runtime.executiongraph.AccessExecutionGraph
import org.apache.flink.runtime.jobgraph.{JobGraph, JobStatus}
import org.apache.flink.test.util.MiniClusterWithClientResource
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig

import java.util.concurrent.CompletableFuture
import scala.collection.JavaConverters._

class Flink19MiniClusterHolder(flinkMiniCluster: MiniClusterWithClientResource,
                               protected val userFlinkClusterConfig: Configuration,
                               protected val envConfig: AdditionalEnvironmentConfig) extends FlinkMiniClusterHolder {

  override def start(): Unit = {
    flinkMiniCluster.before()
    flinkMiniCluster.getClusterClient.setDetached(envConfig.detachedClient)
  }

  override def stop(): Unit = {
    flinkMiniCluster.after()
  }

  override def cancelJob(jobID: JobID): Unit =
    flinkMiniCluster.getClusterClient.cancel(jobID)

  override def submitJob(jobGraph: JobGraph): JobID =
    flinkMiniCluster.getClusterClient.submitJob(jobGraph, getClass.getClassLoader).getJobID

  override def listJobs(): List[JobStatusMessage] =
    flinkMiniCluster.getClusterClient.listJobs().get().asScala.toList

  override def runningJobs(): List[JobID] =
    listJobs().filter(_.getJobState == JobStatus.RUNNING).map(_.getJobId)

  def getClusterClient: ClusterClient[_] = flinkMiniCluster.getClusterClient

  override def getExecutionGraph(jobId: JobID): CompletableFuture[_ <: AccessExecutionGraph] =
    flinkMiniCluster.getMiniCluster.getExecutionGraph(jobId)

}
