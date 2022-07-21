package pl.touk.nussknacker.compatibility

import pl.touk.nussknacker.compatibility.common.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentSchemaBasedSerdeProvider}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider

class MockKafkaComponentProvider extends FlinkKafkaComponentProvider {

  override def providerName: String = "mockKafka"

  protected def createAvroSchemaRegistryProvider: SchemaBasedSerdeProvider = ConfluentSchemaBasedSerdeProvider.avroPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

  protected def createJsonSchemaRegistryProvider: SchemaBasedSerdeProvider = ConfluentSchemaBasedSerdeProvider.jsonPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))
}
