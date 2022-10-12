package pl.touk.nussknacker.compatibility

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.java.tuple
import org.apache.flink.contrib.streaming.state.{PredefinedOptions, RocksDBStateBackend}
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.operators.{SimpleOperatorFactory, StreamOperatorFactory}
import org.apache.flink.streaming.runtime.operators.TimestampsAndWatermarksOperator
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.process.registrar.{DefaultStreamExecutionEnvPreparer, StreamExecutionEnvPreparer}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer, FlinkCompatibilityProvider}

import scala.jdk.CollectionConverters.asScalaSetConverter

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
        val checkpointDataUri = config.checkpointDataUri.getOrElse(throw new IllegalArgumentException("To enable rocksdb state backend, rocksDB.checkpointDataUri must be configured"))
        val rocksDBStateBackend = new RocksDBStateBackend(checkpointDataUri, config.incrementalCheckpoints)
        config.dbStoragePath.foreach(rocksDBStateBackend.setDbStoragePath)
        rocksDBStateBackend.setPredefinedOptions(PredefinedOptions.SPINNING_DISK_OPTIMIZED)
        env.setStateBackend(rocksDBStateBackend: StateBackend)
      }

      override def postRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: FlinkProcessCompilerData, deploymentData: DeploymentData): Unit = {
        super.postRegistration(env, compiledProcessWithDeps, deploymentData)
        val hasWatermarks = env.getStreamGraph("", false).getAllOperatorFactory
          .asScala.toSet[tuple.Tuple2[Integer, StreamOperatorFactory[_]]].map(_.f1).exists {
          case factory: SimpleOperatorFactory[_] => factory.getOperator.isInstanceOf[TimestampsAndWatermarksOperator[_]]
          case _ => false
        }
        if (hasWatermarks) {
          env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
          logger.info("Watermark assignment detected, using EventTime")
        } else {
          env.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime)
          logger.info("Watermark assignment not detected, using IngestionTime")
        }
      }
    }

  }
}
