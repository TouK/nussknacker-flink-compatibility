package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.engine.spel.Implicits._

class FlinkStreamingDeploymentManagerSpec extends FunSuite with Matchers with StreamingDockerTest {

  override protected def classPath: String = s"./flink111/model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flink111-model-assembly.jar"

  test("deploy scenario in running flink") {
    val processId = "runningFlink"

    val version = ProcessVersion(15, ProcessName(processId), "user1", Some(13))
    val process = prepareProcess(processId, Some(1))

    deployProcessAndWaitIfRunning(process, version)

    processVersion(ProcessName(processId)) shouldBe Some(version)

    cancelProcess(processId)
  }

  def prepareProcess(id: String, parallelism: Option[Int] = None) : EspProcess = {
    val baseProcessBuilder = EspProcessBuilder.id(id)
    parallelism.map(baseProcessBuilder.parallelism).getOrElse(baseProcessBuilder)
      .exceptionHandler()
      .source("startProcess", "periodic",
        "period" -> "T(java.time.Duration).ofSeconds(10)",
        "count" -> "1",
        "value" -> "'dummy'")
      .filter("nightFilter", "true")
      .emptySink("dead-end", "dead-end")
  }

  private def processVersion(processId: ProcessName): Option[ProcessVersion] =
    deploymentManager.findJobStatus(processId).futureValue.flatMap(_.version)
}
