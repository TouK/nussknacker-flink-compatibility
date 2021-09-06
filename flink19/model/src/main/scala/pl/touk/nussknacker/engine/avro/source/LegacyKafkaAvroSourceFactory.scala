package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedSingleParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.flink.api.process.FlinkContextInitializer
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka.source.KafkaSource.defaultMaxOutOfOrdernessMillis
import pl.touk.nussknacker.engine.kafka.source.{ConsumerRecordBasedKafkaSource, KafkaSource}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

import scala.reflect.ClassTag

//We have to be able to use LegacyTimestampWatermarkHandler
class LegacyKafkaAvroSourceFactory[K: ClassTag, V: ClassTag](schemaRegistryProvider: SchemaRegistryProvider,
                                                             processObjectDependencies: ProcessObjectDependencies,
                                                             timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]])
  extends KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, timestampAssigner) {
  override protected def createSource(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[KafkaAvroSourceFactory.KafkaAvroSourceFactoryState[K, V, DefinedSingleParameter]], preparedTopics: List[PreparedKafkaTopic], kafkaConfig: KafkaConfig, deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]], timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]], formatter: RecordFormatter, flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]]): KafkaSource[ConsumerRecord[K, V]] = {
    new LegacyConsumerRecordBasedKafkaSource(preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer)

  }
}

class LegacyConsumerRecordBasedKafkaSource[K, V](preparedTopics: List[PreparedKafkaTopic], kafkaConfig: KafkaConfig, deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]], passedAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]], formatter: RecordFormatter, flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]]) extends
  ConsumerRecordBasedKafkaSource[K, V](preparedTopics, kafkaConfig, deserializationSchema, passedAssigner, formatter, flinkContextInitializer) {
  override def timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]] = Some(
    passedAssigner.getOrElse(
      new LegacyTimestampWatermarkHandler[ConsumerRecord[K, V]](
        new BoundedOutOfOrderPreviousElementAssigner[ConsumerRecord[K, V]](
          kafkaConfig.defaultMaxOutOfOrdernessMillis.getOrElse(defaultMaxOutOfOrdernessMillis)
        )
      )
    ))
}

