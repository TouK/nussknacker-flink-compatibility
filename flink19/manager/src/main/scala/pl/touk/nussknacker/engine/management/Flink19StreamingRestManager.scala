package pl.touk.nussknacker.engine.management

import org.apache.flink.runtime.jobgraph.JobStatus
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.management.flinkRestModel.JobOverview
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

class Flink19StreamingRestManager(config: FlinkConfig, modelData: ModelData)(implicit backend: SttpBackend[Future, Nothing, NothingT])
  extends FlinkStreamingRestManager(config, modelData)(backend) {

  //NOTE: Flink <1.10 compatibility - protected to make it easier to work with Flink 1.9, JobStatus changed package, so we use String in case class
  override protected def isNotFinished(overview: JobOverview): Boolean = {
    !JobStatus.valueOf(overview.state).isGloballyTerminalState
  }

  //NOTE: Flink <1.10 compatibility - protected to make it easier to work with Flink 1.9, JobStatus changed package, so we use String in case class
  override protected def mapJobStatus(overview: JobOverview): StateStatus = {
    JobStatus.valueOf(overview.state) match {
      case JobStatus.RUNNING => FlinkStateStatus.Running
      case JobStatus.FINISHED => FlinkStateStatus.Finished
      case JobStatus.RESTARTING => FlinkStateStatus.Restarting
      case JobStatus.CANCELED => FlinkStateStatus.Canceled
      case JobStatus.CANCELLING => FlinkStateStatus.DuringCancel
      //The job is not technically running, but should be in a moment
      case JobStatus.RECONCILING | JobStatus.CREATED | JobStatus.SUSPENDED => FlinkStateStatus.Running
      case JobStatus.FAILING => FlinkStateStatus.Failing
      case JobStatus.FAILED => FlinkStateStatus.Failed
    }
  }
}
