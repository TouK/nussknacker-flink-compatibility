package pl.touk.nussknacker.compatibility

import io.circe.Json
import org.scalatest.FunSuite
import pl.touk.nussknacker.compatibility.common.BaseGenericITSpec
import pl.touk.nussknacker.compatibility.common.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.compatibility.flink19.Flink19Spec
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.genericmodel.{GenericConfigCreator, LegacyGenericConfigCreator}

class GenericITSpec extends FunSuite with BaseGenericITSpec with Flink19Spec  {

  override lazy val creator: GenericConfigCreator = new LegacyGenericConfigCreator {

    override protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.avroPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

    override protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

  }

  //we handle nulls differently in 1.9 for some reason...
  override protected def parseProcessedJson(str: String): Json = super.parseProcessedJson(str).mapObject(_.filter(_._2 != Json.Null))
}
