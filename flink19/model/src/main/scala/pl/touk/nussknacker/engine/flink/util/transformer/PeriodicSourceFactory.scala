package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.LegacyTimestampWatermarkHandler

import java.{util => jul}
import scala.collection.JavaConverters._

//We have to be able to use LegacyTimestampWatermarkHandler
object LegacyPeriodicSourceFactory extends PeriodicSourceFactory(
  new LegacyTimestampWatermarkHandler(new LegacyMapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField)))

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