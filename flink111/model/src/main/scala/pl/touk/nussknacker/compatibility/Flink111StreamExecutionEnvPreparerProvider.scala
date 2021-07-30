package pl.touk.nussknacker.compatibility

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.contrib.streaming.state.{PredefinedOptions, RocksDBStateBackend}
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.runtime.state.{AbstractStateBackend, StateBackend}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.process.registrar.{DefaultStreamExecutionEnvPreparer, StreamExecutionEnvPreparer}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer, FlinkCompatibilityProvider}

class Flink111StreamExecutionEnvPreparerProvider extends FlinkCompatibilityProvider with LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  override def createExecutionEnvPreparer(config: Config, executionConfigPreparer: ExecutionConfigPreparer, useDiskState: Boolean): StreamExecutionEnvPreparer = {
    val checkpointConfig = config.getAs[CheckpointConfig](path = "checkpointConfig")
    val diskStateBackend = config.getAs[RocksDBStateBackendConfig]("rocksDB")

    new DefaultStreamExecutionEnvPreparer(checkpointConfig, diskStateBackend, executionConfigPreparer) {

      //Flink 1.11 - from Flink 1.12.0 FlinkUserCodeClassLoaders.childFirst has different signature
      override def flinkClassLoaderSimulation: ClassLoader = {
        FlinkUserCodeClassLoaders.childFirst(Array.empty,
          Thread.currentThread().getContextClassLoader, Array.empty, (t: Throwable) => throw t)
      }

      //Flink 1.11 - from Flink 1.13.0 introduction of EmbeddedRocksDBStateBackend class
      override protected def configureRocksDBBackend(env: StreamExecutionEnvironment, config: RocksDBStateBackendConfig): Unit = {
        val rocksDBStateBackend = new RocksDBStateBackend(config.checkpointDataUri, config.incrementalCheckpoints)
        config.dbStoragePath.foreach(rocksDBStateBackend.setDbStoragePath)
        rocksDBStateBackend.setPredefinedOptions(PredefinedOptions.SPINNING_DISK_OPTIMIZED)
        env.setStateBackend(rocksDBStateBackend: StateBackend)
      }
    }

  }
}
