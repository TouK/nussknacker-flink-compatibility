package pl.touk.nussknacker.engine.management.common

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.FileSystemBind
import org.apache.commons.io.IOUtils
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.{BindMode, Network}
import org.testcontainers.images.builder.ImageFromDockerfile
import pl.touk.nussknacker.engine.management.common.JobManagerContainer.FlinkJobManagerRestPort
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration

object FlinkContainer {

  def flinkImage(flinkVersion: String): ImageFromDockerfile = {
    val dockerfileWithReplacedScalaVersion = IOUtils
      .toString(getClass.getResourceAsStream("/docker/Dockerfile"), StandardCharsets.UTF_8)
      .replace("${scala.major.version}", ScalaMajorVersionConfig.scalaMajorVersion)
      .replace("${flink.version}", flinkVersion)
    new ImageFromDockerfile()
      .withFileFromString("Dockerfile", dockerfileWithReplacedScalaVersion)
      .withFileFromClasspath("entrypointWithIP.sh", "docker/entrypointWithIP.sh")
      .withFileFromClasspath("conf.yml", "docker/conf.yml")
  }

}

class JobManagerContainer private (underlying: GenericContainer, network: Network)
    extends GenericContainer(underlying) {
  container.setNetwork(network)
  container.addExposedPort(8081)

  def jobmanagerRestUrl: URL = {
    new URL(s"http://$containerIpAddress:${mappedPort(FlinkJobManagerRestPort)}")
  }

}

object JobManagerContainer {
  val FlinkJobManagerRestPort = 8081

  case class Def(flinkVersion: String, savepointDir: Path, network: Network)
      extends GenericContainer.Def[JobManagerContainer](
        new JobManagerContainer(
          GenericContainer(
            FlinkContainer.flinkImage(flinkVersion),
            command = List("jobmanager"),
            env = Map("SAVEPOINT_DIR_NAME" -> savepointDir.getFileName.toString),
            waitStrategy = new LogMessageWaitStrategy()
              .withRegEx(".*Recover all persisted job graphs.*")
              .withStartupTimeout(Duration.ofSeconds(250)),
            fileSystemBind = List(FileSystemBind(savepointDir.toString, savepointDir.toString, BindMode.READ_WRITE))
          ),
          network
        )
      )

}

class TaskManagerContainer private (underlying: GenericContainer, network: Network)
    extends GenericContainer(underlying) {
  underlying.container.setNetwork(network)
}

object TaskManagerContainer {
  private val TaskManagerSlots = 8

  case class Def(flinkVersion: String, network: Network, jobmanagerRpcAddress: String)
      extends GenericContainer.Def[TaskManagerContainer](
        new TaskManagerContainer(
          GenericContainer(
            FlinkContainer.flinkImage(flinkVersion),
            command = List("taskmanager"),
            env = Map(
              "TASK_MANAGER_NUMBER_OF_TASK_SLOTS" -> TaskManagerSlots.toString,
              "JOB_MANAGER_RPC_ADDRESS"           -> jobmanagerRpcAddress
            ),
            waitStrategy = new LogMessageWaitStrategy().withRegEx(".*Successful registration at resource manager.*")
          ),
          network
        )
      )

}
