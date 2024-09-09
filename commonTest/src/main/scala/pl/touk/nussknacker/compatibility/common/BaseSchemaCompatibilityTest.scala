package pl.touk.nussknacker.compatibility.common

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

@silent("deprecated")
trait BaseSchemaCompatibilityTest extends AnyFunSuite with Matchers {

  test("Cheks schema compatibility") {
    val detection = new TypingResultAwareTypeInformationDetection
    val typingResult = Typed.record(Map("int" -> Typed[Int]))
    val executionConfigWithoutKryo: ExecutionConfig = new ExecutionConfig
    val serializerSnapshot = detection.forType(typingResult)
      .createSerializer(executionConfigWithoutKryo)
      .snapshotConfiguration()
    serializerSnapshot.resolveSchemaCompatibility(serializerSnapshot).isCompatibleAsIs shouldBe true
  }
}
