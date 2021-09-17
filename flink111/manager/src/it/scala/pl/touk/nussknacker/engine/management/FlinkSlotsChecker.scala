package pl.touk.nussknacker.engine.management

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{ExternalDeploymentId, ProcessDeploymentData}
import pl.touk.nussknacker.engine.management.rest.HttpFlinkClient

import scala.concurrent.{ExecutionContext, Future}

class FlinkSlotsChecker(client: HttpFlinkClient)(implicit ec: ExecutionContext) extends LazyLogging {

  def checkRequiredSlotsExceedAvailableSlots(processDeploymentData: ProcessDeploymentData, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit] = {
    Future.successful(())
  }
}