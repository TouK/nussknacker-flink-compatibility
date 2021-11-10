package pl.touk.nussknacker.engine.management

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class Flink111StreamingDeploymentManagerProvider extends FlinkStreamingDeploymentManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._


  override def createDeploymentManager(modelData: ModelData, config: Config)(implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager = {
    val flinkConfig = config.rootAs[FlinkConfig]
    new Flink111StreamingRestManager(flinkConfig, modelData)
  }

  override def name: String = "flink111Streaming"
}
