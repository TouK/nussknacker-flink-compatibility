package pl.touk.nussknacker.engine.management.streaming

import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

//TODO: get rid of at least some Thread.sleep
class FlinkStreamingDeploymentManagerSpec extends FunSuite with Matchers with StreamingDockerTest {

  import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._

  override protected def classPath: String = s"./flink111/model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flink111-model-assembly.jar"

  private val defaultDeploymentData = DeploymentData.empty
  
  test("deploy scenario in running flink") {
    val processId = "runningFlink"

    val version = ProcessVersion(15, ProcessName(processId), "user1", Some(13))
    val process = SampleProcess.prepareProcess(processId, Some(1))

    deployProcessAndWaitIfRunning(process, version)

    processVersion(ProcessName(processId)) shouldBe Some(version)

    cancelProcess(processId)
  }

  //this is for the case where e.g. we manually cancel flink job, or it fail and didn't restart...
  ignore("cancel of not existing job should not fail") {
    deploymentManager.cancel(ProcessName("not existing job"), user = userToAct).futureValue shouldBe (())
  }

  ignore("be able verify&redeploy kafka scenario") {

    val processId = "verifyAndRedeploy"
    val outTopic = s"output-$processId"
    val inTopic = s"input-$processId"

    val kafkaProcess = SampleProcess.kafkaProcess(processId, inTopic)

    logger.info("Kafka client created")

    kafkaClient.createTopic(outTopic, 1)
    kafkaClient.createTopic(inTopic, 1)

    logger.info("Kafka topics created, deploying scenario")

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "1"))

    messagesFromTopic(outTopic, 1).head shouldBe "1"

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "2"))

    messagesFromTopic(outTopic, 2).last shouldBe "2"

    assert(deploymentManager.cancel(ProcessName(kafkaProcess.id), user = userToAct).isReadyWithin(10 seconds))
  }

  def empty(processId: String): ProcessVersion = ProcessVersion.empty.copy(processName = ProcessName(processId))


  private def messagesFromTopic(outTopic: String, count: Int): List[String] = {
    kafkaClient.createConsumer()
      .consume(outTopic)
      .map(_.message()).map(new String(_, StandardCharsets.UTF_8))
      .take(count).toList
  }

  private def processVersion(processId: ProcessName): Option[ProcessVersion] =
    deploymentManager.findJobStatus(processId).futureValue.flatMap(_.version)
}
