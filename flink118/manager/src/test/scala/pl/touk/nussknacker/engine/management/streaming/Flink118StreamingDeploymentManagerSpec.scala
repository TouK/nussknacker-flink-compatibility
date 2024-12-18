package pl.touk.nussknacker.engine.management.streaming

import com.dimafeng.testcontainers.lifecycle.and
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, DeploymentManager, DeploymentUpdateStrategy}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

class Flink118StreamingDeploymentManagerSpec extends AnyFunSuite with Matchers with StreamingDockerTest {

  test("deploy scenario in running flink") {
    ScalaMajorVersionConfig.scalaMajorVersion match {
      case "2.12" =>
        withContainers { case jobManager and _ =>
          val deploymentManager = createDeploymentManager(jobManager.jobmanagerRestUrl)
          val processId         = "runningFlink"

          val version = ProcessVersion(
            VersionId(15),
            ProcessName(processId),
            ProcessId(1),
            labels = List.empty,
            user = "user1",
            modelVersion = Some(13)
          )
          val process = prepareProcess(processId, Some(1))

          deployProcessAndWaitIfRunning(
            process,
            version,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            ),
            deploymentManager
          )

          processVersions(ProcessName(processId), deploymentManager) shouldBe List(version)

          cancelProcess(processId, deploymentManager)
        }
      case "2.13" =>
        logger.info("Scala 2.13 - skipping docker-based test")
    }
  }

  private def prepareProcess(id: String, parallelism: Option[Int] = None): CanonicalProcess = {
    val baseProcessBuilder = ScenarioBuilder.streaming(id)
    parallelism
      .map(baseProcessBuilder.parallelism)
      .getOrElse(baseProcessBuilder)
      .source(
        "startProcess",
        "periodic",
        "period" -> "T(java.time.Duration).ofSeconds(10)".spel,
        "count"  -> "1".spel,
        "value"  -> "'dummy'".spel
      )
      .filter("nightFilter", "true".spel)
      .emptySink("dead-end", "dead-end")
  }

  private def processVersions(processId: ProcessName, deploymentManager: DeploymentManager): List[ProcessVersion] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deploymentManager.getProcessStates(processId).futureValue.value.flatMap(_.version)
  }

  override protected def classPath: String =
    s"./flink118/model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/nussknacker-flink-compatibility-1-18-model-assembly.jar"

  override protected def deploymentManagerProvider: FlinkStreamingDeploymentManagerProvider =
    new FlinkStreamingDeploymentManagerProvider()

  override protected val flinkVersion: String = "1.18.1"

}
