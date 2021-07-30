package pl.touk.nussknacker.compatibility

import io.circe.Json
import net.ceedubs.ficus.Ficus._
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.scalatest.FunSuite
import pl.touk.nussknacker.compatibility.common.BaseGenericITSpec
import pl.touk.nussknacker.compatibility.common.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.compatibility.flink19.Flink19Spec
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandler
import pl.touk.nussknacker.genericmodel.GenericConfigCreator

import scala.concurrent.duration._

class GenericITSpec extends FunSuite with BaseGenericITSpec with Flink19Spec  {

  override lazy val creator: GenericConfigCreator = new GenericConfigCreator {

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

    override protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.avroPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

    override protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))

  }

  //we handle nulls differently in 1.9 for some reason...
  override protected def parseProcessedJson(str: String): Json = super.parseProcessedJson(str).mapObject(_.filter(_._2 != Json.Null))
}
