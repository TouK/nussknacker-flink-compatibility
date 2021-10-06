package pl.touk.nussknacker.genericmodel

import net.ceedubs.ficus.Ficus._
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedSingleParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.avro.source.{LegacyConsumerRecordBasedKafkaSource, LegacyKafkaAvroSourceFactory}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkContextInitializer, FlinkSource}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandler
import pl.touk.nussknacker.engine.flink.util.transformer.LegacyPeriodicSourceFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

import java.util
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class LegacyGenericConfigCreator extends GenericConfigCreator {

  //We have to be able to use LegacyTimestampWatermarkHandler
  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "kafka-json" -> defaultCategory(new GenericJsonSourceFactory(processObjectDependencies) {
        override protected def createSource(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[KafkaSourceFactory.KafkaSourceFactoryState[String, util.Map[_, _], DefinedSingleParameter]], preparedTopics: List[PreparedKafkaTopic], kafkaConfig: KafkaConfig, deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[String, util.Map[_, _]]], timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[String, util.Map[_, _]]]], formatter: RecordFormatter, flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[String, util.Map[_, _]]]): FlinkSource[ConsumerRecord[String, util.Map[_, _]]] = {
          new LegacyConsumerRecordBasedKafkaSource(preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer)
        }
      }),
      "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(processObjectDependencies) {
        override protected def createSource(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[KafkaSourceFactory.KafkaSourceFactoryState[String, TypedMap, DefinedSingleParameter]], preparedTopics: List[PreparedKafkaTopic], kafkaConfig: KafkaConfig, deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[String, TypedMap]], timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[String, TypedMap]]], formatter: RecordFormatter, flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[String, TypedMap]]): FlinkSource[ConsumerRecord[String, TypedMap]] = {
          new LegacyConsumerRecordBasedKafkaSource(preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer)
        }
      }),
      "kafka-avro" -> defaultCategory(new LegacyKafkaAvroSourceFactory(avroSerializingSchemaRegistryProvider, processObjectDependencies, None)),
      "kafka-registry-typed-json" -> defaultCategory(new LegacyKafkaAvroSourceFactory(jsonSerializingSchemaRegistryProvider, processObjectDependencies, None)),
      "periodic" -> defaultCategory(LegacyPeriodicSourceFactory)
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory = {
    ExceptionHandlerFactory.noParams(new ConfigurableExceptionHandler(_, processObjectDependencies, Thread.currentThread().getContextClassLoader) {
      // Flink 1.9 doesnt' support configurable restart strategies - only nk exception handling will be configurable

      override def restartStrategy: RestartStrategies.RestartStrategyConfiguration =
        RestartStrategies.fixedDelayRestart(
          Integer.MAX_VALUE,
          processObjectDependencies.config.getOrElse[FiniteDuration]("delayBetweenAttempts", 10.seconds).toMillis
        )
    })
  }
}
