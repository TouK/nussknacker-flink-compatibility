package pl.touk.nussknacker.compatibility.common

import com.github.ghik.silencer.silent
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.streaming.api.functions.{AssignerWithPunctuatedWatermarks, KeyedProcessFunction}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment, createTypeInformation}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.util.Collector
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuiteLike, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation, FlinkSourceFactory}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandler
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForLongs
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.{api, spel}
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import scala.annotation.nowarn

//This test checks if handling of setTimeCharacteristic based of TimestampAssigner presence works well after removal from Nussknacker
trait BaseTimestampTest extends FunSuiteLike with BeforeAndAfterAll with BeforeAndAfter with Matchers with VeryPatientScalaFutures with LazyLogging {

  protected def flinkMiniCluster: FlinkMiniClusterHolder

  before {
    SinkForLongs.clear()
  }

  test("should handle ingestion time") {
    //we check if ingestion time is used (processingTime would not fire eventTime timer)
    runWithAssigner(None)

    //5 seconds should be enough to run this test...
    SinkForLongs.data.head.toDouble shouldBe (System.currentTimeMillis().toDouble +- 5000)
  }

  test("should handle event time") {

    val customFixedTime = System.currentTimeMillis() + 1000000

    runWithAssigner(Some(new LegacyTimestampWatermarkHandler[String](new FixedWatermarks(customFixedTime))))
    SinkForLongs.data shouldBe List(customFixedTime + CheckTimeTransformer.timeToAdd)
  }

  private def runWithAssigner(assigner: Option[TimestampWatermarkHandler[String]]) = {
    import spel.Implicits._
    val process = EspProcessBuilder.id("timestamps")
      .parallelism(1)
      .exceptionHandler()
      .source("source", "source")
      .customNode("custom", "output", "check")
      .emptySink("log", "log", "value" -> "#output")

    val creator = new TestCreator(assigner)

    val modelData = LocalModelData(prepareConfig, creator)
    val env = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.executeAndWaitForFinished(process.id)()

  }

  private def prepareConfig = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef("not_used"))
      .withValue("rocksDB.checkpointDataUri", fromAnyRef("file:///tmp/rocksDBCheckpointDataUri"))
  }
}

@silent("deprecated")
@nowarn("cat=deprecation")
class FixedWatermarks(customFixedTime: Long) extends AssignerWithPunctuatedWatermarks[String]() {
  override def checkAndGetNextWatermark(lastElement: String, extractedTimestamp: Long): Watermark = new Watermark(extractedTimestamp)

  override def extractTimestamp(element: String, recordTimestamp: Long): Long = customFixedTime
}

class TestCreator(assigner: Option[TimestampWatermarkHandler[String]]) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map("source" -> WithCategories(FlinkSourceFactory.noParam(
      new CollectionSource[String](new ExecutionConfig, List(""), assigner, Typed[String]))))
  }


  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("check" -> WithCategories(CheckTimeTransformer))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map("log" -> WithCategories(SinkForLongs.toSourceFactory))
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory = {
    ExceptionHandlerFactory.noParams(new ConfigurableExceptionHandler(_, processObjectDependencies, Thread.currentThread().getContextClassLoader) {
      override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()
    })
  }
}

//We emit event with timestamp 10ms after time of event - to check how timers work
object CheckTimeTransformer extends CustomStreamTransformer {

  val timeToAdd = 10

  @MethodToInvoke
  def invoke(): FlinkCustomStreamTransformation = {
    (start: DataStream[Context], _: FlinkCustomNodeContext) => {
      start.keyBy(_ => "").process(new KeyedProcessFunction[String, Context, ValueWithContext[AnyRef]] {

        override def processElement(value: api.Context, ctx: KeyedProcessFunction[String, api.Context, ValueWithContext[AnyRef]]#Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
          ctx.timerService().registerEventTimeTimer(ctx.timestamp() + timeToAdd)
        }

        override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[String, api.Context, ValueWithContext[AnyRef]]#OnTimerContext, out: Collector[ValueWithContext[AnyRef]]): Unit = {
          out.collect(ValueWithContext(timestamp: java.lang.Long, api.Context("")))
        }
      })
    }
  }

}
