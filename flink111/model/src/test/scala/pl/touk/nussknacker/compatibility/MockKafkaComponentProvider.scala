package pl.touk.nussknacker.compatibility

import pl.touk.nussknacker.compatibility.common.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.flink.util.transformer.KafkaComponentsProvider

class MockKafkaComponentProvider extends KafkaComponentsProvider {

  override def providerName: String = "mockKafka"

  override protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.avroPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

  override protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))
}
