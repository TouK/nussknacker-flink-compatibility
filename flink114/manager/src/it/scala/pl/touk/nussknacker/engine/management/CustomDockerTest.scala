package pl.touk.nussknacker.engine.management

import org.scalatest.Suite
import pl.touk.nussknacker.engine.management.common.DockerTest
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

trait CustomDockerTest extends DockerTest {
  self: Suite =>

  protected override val flinkEsp = s"flinkesp:1.14.5-scala_${ScalaMajorVersionConfig.scalaMajorVersion}"
}
