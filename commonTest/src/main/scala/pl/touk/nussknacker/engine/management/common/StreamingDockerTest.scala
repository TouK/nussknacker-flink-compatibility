package pl.touk.nussknacker.engine.management.common

import akka.actor.ActorSystem
import com.whisk.docker.DockerContainer
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, Suite}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, DeploymentManager, ProcessingTypeDeploymentService, ProcessingTypeDeploymentServiceStub}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future
import scala.concurrent.duration._

trait StreamingDockerTest extends DockerTest with Matchers {
  self: Suite =>

  protected def deploymentManagerProvider: FlinkStreamingDeploymentManagerProvider

  lazy val taskManagerContainer: DockerContainer = buildTaskManagerContainer()
  private implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  implicit val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  implicit val deploymentService: ProcessingTypeDeploymentService = new ProcessingTypeDeploymentServiceStub(List.empty)

  abstract override def dockerContainers: List[DockerContainer] = {
    List(
      zookeeperContainer,
      jobManagerContainer,
      taskManagerContainer
    ) ++ super.dockerContainers
  }

  protected lazy val deploymentManager: DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(config)
    deploymentManagerProvider.createDeploymentManager(ModelData(typeConfig), typeConfig.deploymentConfig)
  }

  protected def deployProcessAndWaitIfRunning(process: CanonicalProcess, processVersion: ProcessVersion, savepointPath: Option[String] = None): Assertion = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deployProcess(process, processVersion, savepointPath)

    eventually {
      val jobStatus = deploymentManager.getProcessState(ProcessName(process.id)).futureValue
      logger.info(s"Waiting for deploy: ${process.id}, $jobStatus")

      jobStatus.value.map(_.status.name) shouldBe Some(SimpleStateStatus.Running.name)
      jobStatus.value.map(_.status.isRunning) shouldBe Some(true)
    }
  }

  protected def deployProcess(process: CanonicalProcess, processVersion: ProcessVersion, savepointPath: Option[String] = None): Assertion = {
    assert(deploymentManager.deploy(processVersion, DeploymentData.empty, process, savepointPath).isReadyWithin(100 seconds))
  }

  protected def cancelProcess(processId: String): Unit = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    assert(deploymentManager.cancel(ProcessName(processId), user = userToAct).isReadyWithin(10 seconds))
    eventually {
      val runningJobs = deploymentManager
        .getProcessState(ProcessName(processId))
        .futureValue
        .value
        .filter(_.status.isRunning)

      logger.debug(s"waiting for jobs: $processId, $runningJobs")
      if (runningJobs.nonEmpty) {
        logger.info(s"RUNNING JOBS: $runningJobs")
        throw new IllegalStateException("Job still exists")
      }
    }
  }

}
