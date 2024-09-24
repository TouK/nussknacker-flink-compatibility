package pl.touk.nussknacker.engine.schemedkafka.kryo

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils

class PreFlink119AvroSerializersRegistrar extends AvroSerializersRegistrar with LazyLogging {

  protected override def registerAvroSerializers(executionConfig: ExecutionConfig): Unit = {
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(
      executionConfig,
      classOf[GenericData.Record]
    )
  }

}
