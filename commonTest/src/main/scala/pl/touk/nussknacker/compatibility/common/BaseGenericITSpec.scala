package pl.touk.nussknacker.compatibility.common


import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.util.transformer.{FlinkBaseComponentProvider, FlinkKafkaComponentProvider}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaTestUtils}
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{ProcessSettingsPreparer, UnoptimizedSerializationPreparer}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.schemedkafka._
import pl.touk.nussknacker.engine.schemedkafka.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.schemedkafka.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaRegistryClientFactory, SchemaVersionOption}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

/*
  This trait should be based on GenericITSpec:
  https://github.com/TouK/nussknacker/blob/staging/engine/flink/tests/src/test/scala/pl/touk/nussknacker/defaultmodel/GenericItSpec.scala
  Following changes are made ATM:
  - `extends FunSuite with FlinkSpec` replaced with `extends FunSuiteLike` ==> FlinkSpec to be defined for each compatibility version
  - flinkMiniCluster: FlinkMiniClusterHolder  ==> should be defined in Flink*Spec
  - `def creator: GenericConfigCreator` made protected
  - checkpointDataUri config added
 */
trait BaseGenericITSpec extends AnyFunSuiteLike with Matchers with KafkaSpec with LazyLogging {

  protected def flinkMiniCluster: FlinkMiniClusterHolder

  import KafkaTestUtils._
  import MockSchemaRegistry._
  import spel.Implicits._
  import KafkaUniversalComponentTransformer._

  override lazy val config: Config = ConfigFactory.load()
    .withValue("components.mockKafka.config.kafkaAddress", fromAnyRef(kafkaServer.kafkaAddress))
    .withValue("components.mockKafka.config.lowLevelComponentsEnabled", fromAnyRef(true))
    .withValue("components.kafka.disabled", fromAnyRef(true))
    .withValue("components.mockKafka.disabled", fromAnyRef(false))
    .withValue("components.mockKafka.config.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
    // we turn off auto registration to do it on our own passing mocked schema registry client
    //For tests we want to read from the beginning...
    .withValue("components.mockKafka.config.kafkaProperties.\"auto.offset.reset\"", fromAnyRef("earliest"))
    .withValue(s"components.mockKafka.config.kafkaEspProperties.${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty}", fromAnyRef(false))
    .withValue("rocksDB.checkpointDataUri", fromAnyRef("file:///tmp/rocksDBCheckpointDataUri"))

  private val secondsToWaitForAvro = 30

  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "components.mockKafka.config")

  protected val avroEncoder: BestEffortAvroEncoder = BestEffortAvroEncoder(ValidationMode.strict)

  private val givenNotMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("first" -> "Zenon", "last" -> "Nowak"), RecordSchemaV1
  )

  private val givenMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "last" -> "Kowalski"), RecordSchemaV1
  )

  private val givenMatchingAvroObjConvertedToV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> null, "last" -> "Kowalski"), RecordSchemaV2
  )

  private val givenMatchingAvroObjV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> "Tomek", "last" -> "Kowalski"), RecordSchemaV2
  )

  private val givenSecondMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("firstname" -> "Jan"), SecondRecordSchemaV1
  )

  private def avroProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption, validationMode: ValidationMode = ValidationMode.strict) =
    ScenarioBuilder
      .streaming("avro-test")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        topicParamName.value -> s"'${topicConfig.input}'",
        schemaVersionParamName.value -> versionOptionParam(versionOption)
      )
      .filter("name-filter", "#input.first == 'Jan'")
      .emptySink(
        "end",
        "kafka",
        sinkKeyParamName.value -> "",
        sinkRawEditorParamName.value -> "true",
        sinkValueParamName.value -> "#input",
        topicParamName.value -> s"'${topicConfig.output}'",
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'",
        sinkValidationModeParamName.value -> s"'${validationMode.name}'"
      )

  private def avroFromScratchProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption) =
    ScenarioBuilder
      .streaming("avro-from-scratch-test")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        topicParamName.value -> s"'${topicConfig.input}'",
        schemaVersionParamName.value -> versionOptionParam(versionOption)
      )
      .emptySink(
        "end",
        "kafka",
        sinkKeyParamName.value -> "",
        sinkRawEditorParamName.value -> "true",
        sinkValueParamName.value -> s"{first: #input.first, last: #input.last}",
        topicParamName.value -> s"'${topicConfig.output}'",
        sinkValidationModeParamName.value -> s"'${ValidationMode.strict.name}'",
        schemaVersionParamName.value -> "'1'"
      )

  private def versionOptionParam(versionOption: SchemaVersionOption) =
    versionOption match {
      case LatestSchemaVersion => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) => s"'$version'"
    }

  test("should read avro object from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo = Instant.now().minus(10, ChronoUnit.HOURS).toEpochMilli

    val topicConfig = createAndRegisterTopicConfig("read-filter-save-avro", RecordSchemas)

    sendAvro(givenNotMatchingAvroObj, topicConfig.input)
    sendAvro(givenMatchingAvroObj, topicConfig.input, timestamp = timeAgo)

    run(avroProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val processed = consumeOneRawAvroMessage(topicConfig.output)
      processed.timestamp shouldBe timeAgo
      valueDeserializer.deserialize(topicConfig.output, processed.value()) shouldEqual givenMatchingAvroObjConvertedToV2
    }
  }

  test("should read avro object from kafka and save new one created from scratch") {
    val topicConfig = createAndRegisterTopicConfig("read-save-scratch", RecordSchemaV1)
    sendAvro(givenMatchingAvroObj, topicConfig.input)

    run(avroFromScratchProcess(topicConfig, ExistingSchemaVersion(1))) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual givenMatchingAvroObj
    }
  }

  test("should read avro object in v1 from kafka and deserialize it to v2, filter and save it to kafka in v2") {
    val topicConfig = createAndRegisterTopicConfig("v1.v2.v2", RecordSchemas)
    val result = avroEncoder.encodeRecordOrError(
      Map("first" -> givenMatchingAvroObj.get("first"), "middle" -> null, "last" -> givenMatchingAvroObj.get("last")),
      RecordSchemaV2
    )

    sendAvro(givenMatchingAvroObj, topicConfig.input)

    run(avroProcess(topicConfig, ExistingSchemaVersion(2))) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual result
    }
  }

  test("should read avro object in v2 from kafka and deserialize it to v1, filter and save it to kafka in v2") {
    val topicConfig = createAndRegisterTopicConfig("v2.v1.v1", RecordSchemas)
    sendAvro(givenMatchingAvroObjV2, topicConfig.input)

    val converted = GenericData.get().deepCopy(RecordSchemaV2, givenMatchingAvroObjV2)
    converted.put("middle", null)

    run(avroProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual converted
    }
  }

  test("should throw exception when record doesn't match to schema") {
    val topicConfig = createAndRegisterTopicConfig("error-record-matching", RecordSchemas)
    val secondTopicConfig = createAndRegisterTopicConfig("error-second-matching", SecondRecordSchemaV1)

    sendAvro(givenSecondMatchingAvroObj, secondTopicConfig.input)

    assertThrows[Exception] {
      run(avroProcess(topicConfig, ExistingSchemaVersion(1))) {
        val processed = consumeOneAvroMessage(topicConfig.output)
        processed shouldEqual givenSecondMatchingAvroObj
      }
    }
  }

  private def parseJson(str: String) = io.circe.parser.parse(str).right.get

  private def consumeOneRawAvroMessage(topic: String) = {
    val consumer = kafkaClient.createConsumer()
    consumer.consumeWithConsumerRecord(topic, secondsToWaitForAvro).head
  }

  private def consumeOneAvroMessage(topic: String) = valueDeserializer.deserialize(topic, consumeOneRawAvroMessage(topic).value())

  protected def creator: DefaultConfigCreator = new DefaultConfigCreator

  private var registrar: FlinkProcessRegistrar = _
  private lazy val valueSerializer = new KafkaAvroSerializer(schemaRegistryMockClient)
  private lazy val valueDeserializer = new KafkaAvroDeserializer(schemaRegistryMockClient)

  class MockFlinkKafkaComponentProvider extends FlinkKafkaComponentProvider {
    override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
      MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val components = FlinkBaseComponentProvider.Components :::
      new MockFlinkKafkaComponentProvider().create(
        config.getConfig("components.mockKafka"),
        ProcessObjectDependencies.withConfig(ConfigFactory.empty())
      )
    val modelData = LocalModelData(config, components, creator)
    registrar = FlinkProcessRegistrar(
      new FlinkProcessCompilerDataFactory(
        creator,
        modelData.extractModelDefinitionFun,
        config,
        modelData.namingStrategy,
        ComponentUseCase.TestRuntime
      ),
      FlinkJobConfig(None, None),
      executionConfigPreparerChain(modelData)
    )
  }

  private def executionConfigPreparerChain(modelData: LocalModelData) = {
    ExecutionConfigPreparer.chain(
      ProcessSettingsPreparer(modelData),
      new UnoptimizedSerializationPreparer(modelData),
      new ExecutionConfigPreparer {
        override def prepareExecutionConfig(config: ExecutionConfig)(jobData: JobData, deploymentData: DeploymentData): Unit = {
          AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(config, new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient), kafkaConfig)
        }
      }
    )
  }

  private def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(env, process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.name.value)(action)
  }

  protected def sendAvro(obj: Any, topic: String, timestamp: java.lang.Long = null): Future[RecordMetadata] = {
    val serializedObj = valueSerializer.serialize(topic, obj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }

  private def sendAsJson(jsonString: String, topic: String, timestamp: java.lang.Long = null) = {
    val serializedObj = jsonString.getBytes(StandardCharsets.UTF_8)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }

  /**
    * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
    * and we can have wrong results of tests..
    */
  private def createAndRegisterTopicConfig(name: String, schemas: List[Schema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      val inputSubject = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
      val outputSubject = ConfluentUtils.topicSubject(topicConfig.output, topicConfig.isKey)
      val parsedSchema = ConfluentUtils.convertToAvroSchema(schema)
      schemaRegistryMockClient.register(inputSubject, parsedSchema)
      schemaRegistryMockClient.register(outputSubject, parsedSchema)
    })

    topicConfig
  }

  protected def createAndRegisterTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))
}

case class TopicConfig(input: String, output: String, schemas: List[Schema], isKey: Boolean)

object TopicConfig {
  private final val inputPrefix = "test.generic.avro.input."
  private final val outputPrefix = "test.generic.avro.output."

  def apply(testName: String, schemas: List[Schema]): TopicConfig =
    new TopicConfig(inputPrefix + testName, outputPrefix + testName, schemas, isKey = false)
}

object MockSchemaRegistry extends Serializable {

  val RecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val RecordSchemaV1: Schema = AvroUtils.parseSchema(RecordSchemaStringV1)

  val RecordSchemaStringV2: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val RecordSchemaV2: Schema = AvroUtils.parseSchema(RecordSchemaStringV2)

  val RecordSchemas: List[Schema] = List(RecordSchemaV1, RecordSchemaV2)

  val SecondRecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "firstname", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val SecondRecordSchemaV1: Schema = AvroUtils.parseSchema(SecondRecordSchemaStringV1)

  val schemaRegistryMockClient: MockSchemaRegistryClient = new MockSchemaRegistryClient

}
