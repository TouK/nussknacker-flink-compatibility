package pl.touk.nussknacker.engine.schemedkafka.kryo

import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils

class Flink114AvroUtilsCompatibilityProvider extends AvroUtilsCompatibilityProvider {

  override def addAvroSerializersIfRequired(executionConfig: ExecutionConfig): Unit = {
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(
      executionConfig,
      classOf[GenericData.Record]
    )
  }

}
