package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.whisk.docker.{ContainerLink, DockerContainer, DockerReadyChecker}
import org.scalatest.{Assertion, Matchers, Suite}
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, DeploymentManager, GraphProcess}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.{DockerTest, Flink111StreamingDeploymentManagerProvider, FlinkStateStatus, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._

trait StreamingDockerTest extends DockerTest with Matchers { self: Suite =>

  lazy val taskManagerContainer: DockerContainer = buildTaskManagerContainer()

  abstract override def dockerContainers: List[DockerContainer] = {
    List(
      zookeeperContainer,
      jobManagerContainer,
      taskManagerContainer
    ) ++ super.dockerContainers
  }

  protected lazy val deploymentManager: DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(config)
    new Flink111StreamingDeploymentManagerProvider().createDeploymentManager(typeConfig.toModelData, typeConfig.deploymentConfig)
  }

  protected def deployProcessAndWaitIfRunning(process: EspProcess, processVersion: ProcessVersion, savepointPath : Option[String] = None): Assertion = {
    deployProcess(process, processVersion, savepointPath)

    eventually {
      val jobStatus = deploymentManager.findJobStatus(ProcessName(process.id)).futureValue
      logger.info(s"Waiting for deploy: ${process.id}, $jobStatus")

      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
      jobStatus.map(_.status.isRunning) shouldBe Some(true)
    }
  }

  protected def deployProcess(process: EspProcess, processVersion: ProcessVersion, savepointPath : Option[String] = None): Assertion = {
    val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    assert(deploymentManager.deploy(processVersion, DeploymentData.empty, GraphProcess(marshaled), savepointPath).isReadyWithin(100 seconds))
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
        logger.info(s"RUNNING JOBS: ${runningJobs}")
        throw new IllegalStateException("Job still exists")
      }
    }
  }

}
