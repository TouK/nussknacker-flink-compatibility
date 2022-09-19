package pl.touk.nussknacker.engine.management.common

import org.apache.flink.api.common.JobStatus
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkStreamingRestManager}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class CommonFlinkStreamingRestManager(config: FlinkConfig, modelData: BaseModelData)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT])
  extends FlinkStreamingRestManager(config, modelData)(ec, backend) {

  override protected def checkDuringDeployForNotRunningJob(s: JobStatus): Boolean = s == JobStatus.RUNNING

}
