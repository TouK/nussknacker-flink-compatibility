package pl.touk.nussknacker.compatibility

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.java.tuple
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.streaming.api.operators.StreamOperatorFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import pl.touk.nussknacker.engine.process.registrar.{DefaultStreamExecutionEnvPreparer, StreamExecutionEnvPreparer}
import pl.touk.nussknacker.engine.process.util.StateConfiguration
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
    val diskStateBackend = None
    config.getAs[RocksDBStateBackendConfig]("rocksDB")
      .map(StateConfiguration.prepareRocksDBStateBackend).filter(_ => useDiskState)

    //Flink 1.9 - from Flink 1.10.2 FlinkUserCodeClassLoaders.childFirst has different signature
    new DefaultStreamExecutionEnvPreparer(checkpointConfig, diskStateBackend, executionConfigPreparer) {
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
    }

  }
}
