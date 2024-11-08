package pl.touk.nussknacker.compatibility

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.typeinformation.{FlinkTypeInfoRegistrar, TypeInformationDetection}

@silent("deprecated")
class SchemaCompatibilityTest extends AnyFunSuite with Matchers {

  test("Checks schema compatibility") {
    FlinkTypeInfoRegistrar.disableFlinkTypeInfoRegistration()
    val detection                                   = TypeInformationDetection.instance
    val typingResult                                = Typed.record(Map("int" -> Typed[Int]))
    val executionConfigWithoutKryo: ExecutionConfig = new ExecutionConfig
    val serializerSnapshot = detection
      .forType(typingResult)
      .createSerializer(executionConfigWithoutKryo)
    serializerSnapshot
      .snapshotConfiguration()
      .resolveSchemaCompatibility(serializerSnapshot)
      .isCompatibleAsIs shouldBe true
  }

}
