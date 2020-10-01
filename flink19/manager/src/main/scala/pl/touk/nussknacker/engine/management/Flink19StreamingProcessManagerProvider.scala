package pl.touk.nussknacker.engine.management
import com.typesafe.config.Config
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

class Flink19StreamingProcessManagerProvider extends FlinkStreamingProcessManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createProcessManager(modelData: ModelData, config: Config): ProcessManager = {
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

    val flinkConfig = config.rootAs[FlinkConfig]
    new Flink19StreamingRestManager(flinkConfig, modelData)
  }

  override def name: String = "flink19Streaming"
}
