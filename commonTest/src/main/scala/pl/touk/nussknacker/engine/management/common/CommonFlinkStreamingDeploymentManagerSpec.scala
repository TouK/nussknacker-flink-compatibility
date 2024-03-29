package pl.touk.nussknacker.engine.management.common

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.Implicits._

trait CommonFlinkStreamingDeploymentManagerSpec extends AnyFunSuite with Matchers with StreamingDockerTest {
  test("deploy scenario in running flink") {
    val processId = "runningFlink"

    val version = ProcessVersion(VersionId(15), ProcessName(processId), ProcessId(1), "user1", Some(13))
    val process = prepareProcess(processId, Some(1))

    deployProcessAndWaitIfRunning(process, version)

    processVersions(ProcessName(processId)) shouldBe List(version)

    cancelProcess(processId)
  }

  def prepareProcess(id: String, parallelism: Option[Int] = None) : CanonicalProcess = {
    val baseProcessBuilder = ScenarioBuilder.streaming(id)
    parallelism.map(baseProcessBuilder.parallelism).getOrElse(baseProcessBuilder)
      .source("startProcess", "periodic",
        "period" -> "T(java.time.Duration).ofSeconds(10)",
        "count" -> "1",
        "value" -> "'dummy'")
      .filter("nightFilter", "true")
      .emptySink("dead-end", "dead-end")
  }

  private def processVersions(processId: ProcessName): List[ProcessVersion] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deploymentManager.getProcessStates(processId).futureValue.value.flatMap(_.version)
  }
}
