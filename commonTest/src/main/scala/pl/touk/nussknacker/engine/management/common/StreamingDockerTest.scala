package pl.touk.nussknacker.engine.management.common

import akka.actor.ActorSystem
import com.dimafeng.testcontainers.lifecycle.and
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Minutes, Span}
import org.scalatest.{Assertion, Suite}
import org.testcontainers.containers.Network
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{CancelScenarioCommand, DataFreshnessPolicy, DeploymentManager, ProcessingTypeDeploymentServiceStub, RunDeploymentCommand}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, User}
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.net.URL
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path}
import java.util.Collections
import scala.concurrent.Future
import scala.concurrent.duration._

trait StreamingDockerTest extends TestContainersForAll
  with Matchers
  with ScalaFutures
  with Eventually
  with LazyLogging {
  self: Suite =>

  override type Containers = JobManagerContainer and TaskManagerContainer

  protected val flinkVersion: String

  private val userToAct: User = User("testUser", "Test User")

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(2, Minutes)), interval = scaled(Span(100, Millis)))

  protected def deploymentManagerProvider: FlinkStreamingDeploymentManagerProvider

  private val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  private val backend: SttpBackend[Future, Any] =
    AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())


  override def startContainers(): Containers = {
    val network = Network.newNetwork()
    val volumeDir = prepareVolumeDir()
    val jobmanager: JobManagerContainer = JobManagerContainer.Def(flinkVersion, volumeDir, network).start()
    val jobmanagerHostName = jobmanager.container.getContainerInfo.getConfig.getHostName
    val taskmanager: TaskManagerContainer = TaskManagerContainer.Def(flinkVersion, network, jobmanagerHostName).start()
    jobmanager and taskmanager
  }

  private def prepareVolumeDir(): Path = {
    import scala.collection.JavaConverters._
    Files.createTempDirectory("dockerTest",
      PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet[PosixFilePermission].asJava))
  }

  protected def createDeploymentManager(jobmanagerRestUrl: URL): DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(config(jobmanagerRestUrl))
    val modelDependencies: ModelDependencies = {
      ModelDependencies(
        additionalConfigsFromProvider = Map.empty,
        determineDesignerWideId = componentId => DesignerWideComponentId(componentId.toString),
        workingDirectoryOpt = None,
        shouldIncludeComponentProvider = _ => true
      )
    }
    val deploymentManagerDependencies = DeploymentManagerDependencies(
      new ProcessingTypeDeploymentServiceStub(List.empty),
      actorSystem.dispatcher,
      actorSystem,
      backend
    )
    deploymentManagerProvider.createDeploymentManager(
      ModelData(typeConfig, modelDependencies),
      deploymentManagerDependencies,
      typeConfig.deploymentConfig,
      None
    ).valueOr(err => throw new IllegalStateException(s"Invalid Deployment Manager: ${err.toList.mkString(", ")}"))
  }

  protected def deployProcessAndWaitIfRunning(process: CanonicalProcess, processVersion: ProcessVersion, savepointPath: Option[String] = None, deploymentManager: DeploymentManager): Assertion = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deployProcess(process, processVersion, savepointPath, deploymentManager)

    eventually {
      val jobStatus = deploymentManager.getProcessStates(process.name).futureValue
      logger.info(s"Waiting for deploy: ${process.name}, $jobStatus")

      jobStatus.value.map(_.status.name) shouldBe List(SimpleStateStatus.Running.name)
    }
  }

  private def deployProcess(process: CanonicalProcess, processVersion: ProcessVersion, savepointPath: Option[String] = None, deploymentManager: DeploymentManager): Assertion = {
    assert(deploymentManager.processCommand(RunDeploymentCommand(processVersion, DeploymentData.empty, process, savepointPath)).isReadyWithin(100 seconds))
  }

  protected def cancelProcess(processId: String, deploymentManager: DeploymentManager): Unit = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    assert(deploymentManager.processCommand(CancelScenarioCommand(ProcessName(processId), user = userToAct)).isReadyWithin(10 seconds))
    eventually {
      val runningJobs = deploymentManager
        .getProcessStates(ProcessName(processId))
        .futureValue
        .value
        .filter(_.status.name == SimpleStateStatus.Running.name)

      logger.debug(s"waiting for jobs: $processId, $runningJobs")
      if (runningJobs.nonEmpty) {
        logger.info(s"RUNNING JOBS: $runningJobs")
        throw new IllegalStateException("Job still exists")
      }
    }
  }

  protected def classPath: String

  private def config(jobManagerRestUrl: URL): ConfigWithUnresolvedVersion = ConfigWithUnresolvedVersion(ConfigFactory.load()
    .withValue("deploymentConfig.restUrl", fromAnyRef(jobManagerRestUrl.toExternalForm))
    .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(Collections.singletonList(classPath)))
    .withValue("category", fromAnyRef("Category1")))

}
