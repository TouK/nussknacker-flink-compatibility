package pl.touk.nussknacker.compatibility.common

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

@silent("deprecated")
trait BaseSchemaCompatibilityTest extends AnyFunSuite with Matchers {

  test("Cheks schema compatibility") {
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
