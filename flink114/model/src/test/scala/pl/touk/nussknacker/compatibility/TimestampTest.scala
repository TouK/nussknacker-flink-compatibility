package pl.touk.nussknacker.compatibility

import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.compatibility.common.BaseTimestampTest
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder

import java.lang

class TimestampTest extends BaseTimestampTest with FlinkSpec with Matchers {
  override protected val sinkForLongsResultsHolder: () => TestResultsHolder[lang.Long] =
    () => TimestampTest.sinkForLongsResultsHolder
}

object TimestampTest extends Serializable {
  private val sinkForLongsResultsHolder = new TestResultsHolder[java.lang.Long]
}
