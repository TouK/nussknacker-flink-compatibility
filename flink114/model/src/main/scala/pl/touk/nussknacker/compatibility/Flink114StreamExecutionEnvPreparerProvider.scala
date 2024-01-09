package pl.touk.nussknacker.compatibility

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.util.OutputTag
import pl.touk.nussknacker.engine.process.registrar.{DefaultStreamExecutionEnvPreparer, StreamExecutionEnvPreparer}
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkCompatibilityProvider, FlinkJobConfig}

class Flink114StreamExecutionEnvPreparerProvider extends FlinkCompatibilityProvider with LazyLogging {

  override def createExecutionEnvPreparer(jobConfig: FlinkJobConfig, executionConfigPreparer: ExecutionConfigPreparer): StreamExecutionEnvPreparer = {

    new DefaultStreamExecutionEnvPreparer(jobConfig, executionConfigPreparer) {

      override def flinkClassLoaderSimulation: ClassLoader = {
        FlinkUserCodeClassLoaders.childFirst(Array.empty,
          Thread.currentThread().getContextClassLoader, Array.empty, (t: Throwable) => throw t, true
        )
      }

      override def sideOutputGetter[T](singleOutputStreamOperator: SingleOutputStreamOperator[_], outputTag: OutputTag[T]): DataStream[T] = {
        singleOutputStreamOperator.getSideOutput(outputTag)
      }

    }
  }
}
