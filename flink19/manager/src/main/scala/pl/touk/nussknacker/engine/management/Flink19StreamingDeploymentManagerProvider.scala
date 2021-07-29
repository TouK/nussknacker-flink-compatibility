package pl.touk.nussknacker.engine.management
import com.typesafe.config.Config
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

class Flink19StreamingDeploymentManagerProvider extends FlinkStreamingDeploymentManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager = {
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

    val flinkConfig = config.rootAs[FlinkConfig]
    new Flink19StreamingRestManager(flinkConfig, modelData)
  }

  override def name: String = "flink19Streaming"
}
