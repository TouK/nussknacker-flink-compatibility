package pl.touk.nussknacker.compatibility

import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.compatibility.common.BaseTimestampTest
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder

import java.lang

class Flink114TimestampTest extends BaseTimestampTest with FlinkSpec with Matchers {
  override protected val sinkForLongsResultsHolder: () => TestResultsHolder[lang.Long] =
    () => Flink114TimestampTest.sinkForLongsResultsHolder
}

object Flink114TimestampTest extends Serializable {
  private val sinkForLongsResultsHolder = new TestResultsHolder[java.lang.Long]
}
