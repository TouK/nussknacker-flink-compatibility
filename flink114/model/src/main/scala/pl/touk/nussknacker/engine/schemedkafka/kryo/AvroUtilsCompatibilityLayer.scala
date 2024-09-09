package pl.touk.nussknacker.engine.schemedkafka.kryo

import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils

/*
    Flink compatibility note:
      for compatibility with flink before 1.19 we need to patch this class since signature of
      AvroUtils.getAvroUtils.addAvroSerializersIfRequired in 1.19 requires SerializerConfig, which doesn't exist
      before 1.19
 */

// We need it because we use avro records inside our Context class
class AvroUtilsCompatibilityLayer {

  private[kryo] def addAvroSerializersIfRequired(executionConfig: ExecutionConfig): Unit = {
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(
      executionConfig,
      classOf[GenericData.Record]
    )
  }

}
