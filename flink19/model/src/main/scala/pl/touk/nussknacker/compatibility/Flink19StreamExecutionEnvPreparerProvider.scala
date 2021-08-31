package pl.touk.nussknacker.compatibility

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.java.tuple
import org.apache.flink.contrib.streaming.state.{PredefinedOptions, RocksDBStateBackend}
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.operators.{SimpleOperatorFactory, StreamOperatorFactory}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.runtime.operators.{TimestampsAndPeriodicWatermarksOperator, TimestampsAndPunctuatedWatermarksOperator}
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.process.registrar.{DefaultStreamExecutionEnvPreparer, StreamExecutionEnvPreparer}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer, FlinkCompatibilityProvider}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

class Flink19StreamExecutionEnvPreparerProvider extends FlinkCompatibilityProvider with LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  override def createExecutionEnvPreparer(config: Config, executionConfigPreparer: ExecutionConfigPreparer, useDiskState: Boolean): StreamExecutionEnvPreparer = {
    // TODO checkpointInterval is deprecated - remove it in future
    val checkpointInterval = config.getAs[FiniteDuration](path = "checkpointInterval")
    if (checkpointInterval.isDefined) {
      logger.warn("checkpointInterval config property is deprecated, use checkpointConfig.checkpointInterval instead")
    }

    val checkpointConfig = config.getAs[CheckpointConfig](path = "checkpointConfig")
      .orElse(checkpointInterval.map(CheckpointConfig(_)))
    val diskStateBackendConfig = config.getAs[RocksDBStateBackendConfig]("rocksDB").filter(_ => useDiskState)

    //Flink 1.9 - from Flink 1.10.2 FlinkUserCodeClassLoaders.childFirst has different signature
    new Flink19StreamExecutionEnvPreparer(checkpointConfig, diskStateBackendConfig, executionConfigPreparer)
  }

}

class Flink19StreamExecutionEnvPreparer(checkpointConfig: Option[CheckpointConfig], diskStateBackend: Option[RocksDBStateBackendConfig], executionConfigPreparer: ExecutionConfigPreparer)
  extends DefaultStreamExecutionEnvPreparer(checkpointConfig, diskStateBackend, executionConfigPreparer) {

  override def flinkClassLoaderSimulation: ClassLoader = {
    FlinkUserCodeClassLoaders.childFirst(Array.empty,
      Thread.currentThread().getContextClassLoader, Array.empty)
  }

  override protected def initializeStateDescriptors(env: StreamExecutionEnvironment): Unit = {
    val config = env.getConfig
    //Flink 1.9 getStreamGraph has different signature
    env.getStreamGraph.getAllOperatorFactory.asScala.toSet[tuple.Tuple2[Integer, StreamOperatorFactory[_]]].map(_.f1).collect {
      case window: WindowOperator[_, _, _, _, _] => window.getStateDescriptor.initializeSerializerUnlessSet(config)
    }
  }

  override def postRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: FlinkProcessCompilerData): Unit = {
    super.postRegistration(env, compiledProcessWithDeps)
    val hasWatermarks = env.getStreamGraph.getAllOperatorFactory
      .asScala.toSet[tuple.Tuple2[Integer, StreamOperatorFactory[_]]].map(_.f1).exists {
      case factory: SimpleOperatorFactory[_] =>
        factory.getOperator.isInstanceOf[TimestampsAndPunctuatedWatermarksOperator[_]] || factory.getOperator.isInstanceOf[TimestampsAndPeriodicWatermarksOperator[_]]
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

  override protected def configureRocksDBBackend(env: StreamExecutionEnvironment, config: RocksDBStateBackendConfig): Unit = {
    val checkpointDataUri = config.checkpointDataUri.getOrElse(throw new IllegalArgumentException("To enable rocksdb state backend, rocksdb.checkpointDataUri must be configured"))
    val rocksDBStateBackend = new RocksDBStateBackend(checkpointDataUri, config.incrementalCheckpoints)
    config.dbStoragePath.foreach(rocksDBStateBackend.setDbStoragePath)
    rocksDBStateBackend.setPredefinedOptions(PredefinedOptions.SPINNING_DISK_OPTIMIZED)
    env.setStateBackend(rocksDBStateBackend: StateBackend)
  }

}

