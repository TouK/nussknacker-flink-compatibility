package pl.touk.nussknacker.engine.management.streaming

import akka.actor.ActorSystem
import com.whisk.docker.DockerContainer
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.{Assertion, Suite}
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService, ProcessingTypeDeploymentServiceStub}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.management.{CustomDockerTest, CustomFlinkStreamingDeploymentManagerProvider, FlinkStateStatus}
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future
import scala.concurrent.duration._

trait StreamingDockerTest extends CustomDockerTest with Matchers {
  self: Suite =>

  lazy val taskManagerContainer: DockerContainer = buildTaskManagerContainer()
  private implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
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
    new CustomFlinkStreamingDeploymentManagerProvider().createDeploymentManager(ModelData(typeConfig), typeConfig.deploymentConfig)
  }

  protected def deployProcessAndWaitIfRunning(process: CanonicalProcess, processVersion: ProcessVersion, savepointPath: Option[String] = None): Assertion = {
    deployProcess(process, processVersion, savepointPath)

    eventually {
      val jobStatus = deploymentManager.findJobStatus(ProcessName(process.id)).futureValue
      logger.info(s"Waiting for deploy: ${process.id}, $jobStatus")

      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
      jobStatus.map(_.status.isRunning) shouldBe Some(true)
    }
  }

  protected def deployProcess(process: CanonicalProcess, processVersion: ProcessVersion, savepointPath: Option[String] = None): Assertion = {
    assert(deploymentManager.deploy(processVersion, DeploymentData.empty, process, savepointPath).isReadyWithin(100 seconds))
  }

  protected def cancelProcess(processId: String): Unit = {
    assert(deploymentManager.cancel(ProcessName(processId), user = userToAct).isReadyWithin(10 seconds))
    eventually {
      val runningJobs = deploymentManager
        .findJobStatus(ProcessName(processId))
        .futureValue
        .filter(_.status.isRunning)

      logger.debug(s"waiting for jobs: $processId, $runningJobs")
      if (runningJobs.nonEmpty) {
        logger.info(s"RUNNING JOBS: $runningJobs")
        throw new IllegalStateException("Job still exists")
      }
    }
  }

}
