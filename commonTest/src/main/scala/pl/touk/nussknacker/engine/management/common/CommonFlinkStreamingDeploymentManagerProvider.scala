package pl.touk.nussknacker.engine.management.common

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkStreamingDeploymentManagerProvider}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class CommonFlinkStreamingDeploymentManagerProvider extends FlinkStreamingDeploymentManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    val flinkConfig = config.rootAs[FlinkConfig]
    new CommonFlinkStreamingRestManager(flinkConfig, modelData)
  }
}
