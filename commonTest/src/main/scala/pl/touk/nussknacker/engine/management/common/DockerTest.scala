package pl.touk.nussknacker.engine.management.common

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import com.whisk.docker.{ContainerLink, DockerContainer, DockerFactory, DockerReadyChecker, LogLineReceiver, VolumeMapping}
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import org.apache.commons.io.{FileUtils, IOUtils}
import org.scalatest.Suite
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Minutes, Span}
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.deployment.User
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path}
import java.util.Collections
import scala.concurrent.duration._

trait DockerTest extends DockerTestKit with ScalaFutures with Eventually with LazyLogging {
  self: Suite =>

  protected def dockerNameSuffix: String
  protected def flinkEsp: String

  override val StartContainersTimeout: FiniteDuration = 5.minutes
  override val StopContainersTimeout: FiniteDuration = 2.minutes

  final override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(2, Minutes)), interval = scaled(Span(100, Millis)))

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()

  protected val userToAct: User = User("testUser", "Test User")

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  //TODO: make pull request to flink out of it?
  private def prepareDockerImage() = {
    val dir = Files.createTempDirectory("forDockerfile")
    val dirFile = dir.toFile

    List("Dockerfile", "entrypointWithIP.sh", "conf.yml").foreach { file =>
      val resource = IOUtils.toString(getClass.getResourceAsStream(s"/docker/$file"), StandardCharsets.UTF_8)
      val withVersionReplaced = resource.replace("${scala.major.version}", ScalaMajorVersionConfig.scalaMajorVersion)
      FileUtils.writeStringToFile(new File(dirFile, file), withVersionReplaced, StandardCharsets.UTF_8)
    }

    client.build(dir, flinkEsp)
  }

  prepareDockerImage()

  val ZookeeperDefaultPort = 2181
  val FlinkJobManagerRestPort = 8081
  val taskManagerSlotCount = 8

  lazy val zookeeperContainer: DockerContainer =
    DockerContainer("wurstmeister/zookeeper:3.4.6", name = Some("zookeeper" ++ dockerNameSuffix))

  def baseFlink(name: String): DockerContainer = DockerContainer(flinkEsp, Some(name))

  lazy val jobManagerContainer: DockerContainer = {
    val savepointDir = prepareVolumeDir()
    baseFlink("jobmanager" ++ dockerNameSuffix)
      .withCommand("jobmanager")
      .withEnv(s"SAVEPOINT_DIR_NAME=${savepointDir.getFileName}")
      .withReadyChecker(DockerReadyChecker.LogLineContains("Recover all persisted job graphs").looped(5, 1 second))
      .withLinks(ContainerLink(zookeeperContainer, "zookeeper"))
      .withVolumes(List(VolumeMapping(savepointDir.toString, savepointDir.toString, rw = true)))
      .withLogLineReceiver(LogLineReceiver(withErr = true, s => {
        logger.debug(s"jobmanager: $s")
      }))
  }

  def buildTaskManagerContainer(additionalLinks: Seq[ContainerLink] = Nil,
                                volumes: Seq[VolumeMapping] = Nil): DockerContainer = {
    val links = List(
      ContainerLink(zookeeperContainer, "zookeeper"),
      ContainerLink(jobManagerContainer, "jobmanager")
    ) ++ additionalLinks
    baseFlink("taskmanager" ++ dockerNameSuffix)
      .withCommand("taskmanager")
      .withEnv(s"TASK_MANAGER_NUMBER_OF_TASK_SLOTS=$taskManagerSlotCount")
      .withReadyChecker(DockerReadyChecker.LogLineContains("Successful registration at resource manager").looped(5, 1 second))
      .withLinks(links :_*)
      .withVolumes(volumes)
      .withLogLineReceiver(LogLineReceiver(withErr = true, s => {
        logger.debug(s"taskmanager: $s")
      }))
  }

  protected def ipOfContainer(container: DockerContainer): String = container.getIpAddresses().futureValue.head

  protected def prepareVolumeDir(): Path = {
    import scala.collection.JavaConverters._
    Files.createTempDirectory("dockerTest",
      PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet[PosixFilePermission].asJava))
  }

  def config: Config = ConfigFactory.load()
    .withValue("deploymentConfig.restUrl", fromAnyRef(s"http://${jobManagerContainer.getIpAddresses().futureValue.head}:$FlinkJobManagerRestPort"))
    .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(Collections.singletonList(classPath)))
    .withFallback(additionalConfig)

  def processingTypeConfig: ProcessingTypeConfig = ProcessingTypeConfig.read(config)

  protected def classPath: String

  protected def additionalConfig: Config = ConfigFactory.empty()

}
