package pl.touk.nussknacker.compatibility.common

import pl.touk.nussknacker.compatibility.common.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory

class MockKafkaComponentProvider extends FlinkKafkaComponentProvider {

  override def providerName: String = "mockKafka"

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
    new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

}
