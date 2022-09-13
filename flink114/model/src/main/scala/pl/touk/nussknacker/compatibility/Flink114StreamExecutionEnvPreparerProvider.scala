package pl.touk.nussknacker.compatibility

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.process.registrar.{DefaultStreamExecutionEnvPreparer, StreamExecutionEnvPreparer}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer, FlinkCompatibilityProvider}

class Flink114StreamExecutionEnvPreparerProvider extends FlinkCompatibilityProvider with LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  override def createExecutionEnvPreparer(config: Config, executionConfigPreparer: ExecutionConfigPreparer, useDiskState: Boolean): StreamExecutionEnvPreparer = {
    val checkpointConfig = config.getAs[CheckpointConfig](path = "checkpointConfig")
    val diskStateBackend = config.getAs[RocksDBStateBackendConfig]("rocksDB")

    new DefaultStreamExecutionEnvPreparer(checkpointConfig, diskStateBackend, executionConfigPreparer)
  }
}
