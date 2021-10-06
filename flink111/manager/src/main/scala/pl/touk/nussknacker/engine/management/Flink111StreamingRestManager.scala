package pl.touk.nussknacker.engine.management

import org.apache.flink.api.common.JobStatus
import pl.touk.nussknacker.engine.ModelData
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

class Flink111StreamingRestManager(config: FlinkConfig, modelData: ModelData)(implicit backend: SttpBackend[Future, Nothing, NothingT])
  extends FlinkStreamingRestManager(config, modelData)(backend) {

  override protected def checkDuringDeployForNotRunningJob(s: JobStatus): Boolean = s == JobStatus.RUNNING

}
