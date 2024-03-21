package pl.touk.nussknacker.compatibility.common

import com.github.ghik.silencer.silent
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.{AssignerWithPunctuatedWatermarks, KeyedProcessFunction}
import org.apache.flink.streaming.api.scala.createTypeInformation
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.util.Collector
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForLongs
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{api, spel}
import pl.touk.nussknacker.test.VeryPatientScalaFutures

//This test checks if handling of setTimeCharacteristic based of TimestampAssigner presence works well after removal from Nussknacker
trait BaseTimestampTest extends AnyFunSuiteLike with BeforeAndAfterAll with BeforeAndAfter with Matchers with VeryPatientScalaFutures with LazyLogging {

  protected val sinkForLongsResultsHolder: () => TestResultsHolder[java.lang.Long]
  protected def flinkMiniCluster: FlinkMiniClusterHolder

  before {
    sinkForLongsResultsHolder().clear()
  }

  test("should handle event time") {
    val customFixedTime = System.currentTimeMillis() + 1000000

    runWithAssigner(Some(new LegacyTimestampWatermarkHandler[String](new FixedWatermarks(customFixedTime))))

    sinkForLongsResultsHolder().results shouldBe List(customFixedTime + CheckTimeTransformer.timeToAdd)
  }

  protected def runWithAssigner(assigner: Option[TimestampWatermarkHandler[String]]): JobExecutionResult = {
    import spel.Implicits._
    val process = ScenarioBuilder.streaming("timestamps")
      .parallelism(1)
      .source("source", "source")
      .customNode("custom", "output", "check")
      .emptySink("log", "log", "Value" -> "#output")

    val creator = new TestCreator(assigner, sinkForLongsResultsHolder())

    val modelData = LocalModelData(prepareConfig, Nil, creator)
    val env = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompilerDataFactory(creator, modelData.extractModelDefinitionFun, prepareConfig, modelData.namingStrategy, ComponentUseCase.TestRuntime),
      FlinkJobConfig(None, None), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(env, process, ProcessVersion.empty, DeploymentData.empty)
    env.executeAndWaitForFinished(process.name.value)()
  }

  private def prepareConfig = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef("not_used"))
      .withValue("rocksDB.checkpointDataUri", fromAnyRef("file:///tmp/rocksDBCheckpointDataUri"))
  }
}

@silent("deprecated")
class FixedWatermarks(customFixedTime: Long) extends AssignerWithPunctuatedWatermarks[String]() {
  override def checkAndGetNextWatermark(lastElement: String, extractedTimestamp: Long): Watermark = new Watermark(extractedTimestamp)

  override def extractTimestamp(element: String, recordTimestamp: Long): Long = customFixedTime
}

class TestCreator(assigner: Option[TimestampWatermarkHandler[String]],
                  sinkForLongsResultsHolder: => TestResultsHolder[java.lang.Long]) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    Map("source" -> WithCategories.anyCategory(SourceFactory.noParamUnboundedStreamFactory[String](
      new CollectionSource[String](List(""), assigner, Typed[String]))))
  }


  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("check" -> WithCategories.anyCategory(CheckTimeTransformer))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map("log" -> WithCategories.anyCategory(SinkForLongs(sinkForLongsResultsHolder)))
  }
}

//We emit event with timestamp 10ms after time of event - to check how timers work
object CheckTimeTransformer extends CustomStreamTransformer {

  val timeToAdd = 10

  @MethodToInvoke
  def invoke(): FlinkCustomStreamTransformation = {
    (start: DataStream[Context], _: FlinkCustomNodeContext) => {
      start.keyBy((_: Context) => "").process(new KeyedProcessFunction[String, Context, ValueWithContext[AnyRef]] {

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
