package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.{BasicContextInitializingFunction, FlinkCustomNodeContext, FlinkSource, FlinkSourceFactory}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}

import java.time.Duration
import java.{util => jul}
import javax.annotation.Nullable
import javax.validation.constraints.Min
import scala.collection.JavaConverters._
import org.apache.flink.api.scala._

//We have to be able to use LegacyTimestampWatermarkHandler
object LegacyPeriodicSourceFactory extends PeriodicSourceFactory(
  new LegacyTimestampWatermarkHandler(new LegacyMapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField)))


class LegacyPeriodicSourceFactory(timestampAssigner: TimestampWatermarkHandler[AnyRef]) extends FlinkSourceFactory[AnyRef]  {

  @MethodToInvoke
  def create(@ParamName("period") period: Duration,
             // TODO: @DefaultValue(1) instead of nullable
             @ParamName("count") @Nullable @Min(1) nullableCount: Integer,
             @ParamName("value") value: LazyParameter[AnyRef]): Source[_] = {
    new FlinkSource[AnyRef] with ReturningType {

      override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {

        val count = Option(nullableCount).map(_.toInt).getOrElse(1)
        val processId = flinkNodeContext.metaData.id
        val stream = env
          .addSource(new PeriodicFunction(period))
          .map(_ => Context(processId))
          .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(value))
          .flatMap { v =>
            1.to(count).map(_ => v.value)
          }

        val rawSourceWithTimestamp = timestampAssigner.assignTimestampAndWatermarks(stream)

        val typeInformationFromNodeContext = flinkNodeContext.typeInformationDetection.forContext(flinkNodeContext.validationContext.left.get)
        rawSourceWithTimestamp
          .map(new BasicContextInitializingFunction[AnyRef](flinkNodeContext.metaData.id, flinkNodeContext.nodeId))(typeInformationFromNodeContext)
      }

      override val returnType: typing.TypingResult = value.returnType

    }
  }

}

class LegacyMapAscendingTimestampExtractor(timestampField: String) extends AscendingTimestampExtractor[AnyRef] {
  override def extractAscendingTimestamp(element: AnyRef): Long = {
    element match {
      case m: jul.Map[String@unchecked, AnyRef@unchecked] =>
        m.asScala
          .get(timestampField).map(_.asInstanceOf[Long])
          .getOrElse(System.currentTimeMillis())
      case _ =>
        System.currentTimeMillis()
    }
  }
}