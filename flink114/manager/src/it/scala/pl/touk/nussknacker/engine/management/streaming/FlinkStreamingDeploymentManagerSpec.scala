package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.CustomFlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.management.common.{CommonFlinkStreamingDeploymentManagerProvider, CommonFlinkStreamingDeploymentManagerSpec}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

class FlinkStreamingDeploymentManagerSpec extends CommonFlinkStreamingDeploymentManagerSpec {

  override protected def classPath: String =
    s"./flink114/model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flink114-model-assembly.jar"

  override protected def deploymentManagerProvider: CommonFlinkStreamingDeploymentManagerProvider =
    new CustomFlinkStreamingDeploymentManagerProvider()

  override protected val flinkEsp =
    s"flinkesp:1.14.5-scala_${ScalaMajorVersionConfig.scalaMajorVersion}"
}
