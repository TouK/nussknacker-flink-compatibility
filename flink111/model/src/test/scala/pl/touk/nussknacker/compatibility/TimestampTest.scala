package pl.touk.nussknacker.compatibility

import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.compatibility.common.BaseTimestampTest
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForLongs

class TimestampTest extends BaseTimestampTest with FlinkSpec with Matchers {

  // This test depends on default time characteristics in Flink 1.11 -
  // - ingestion time. This default was changed to event time in 1.12.
  test("should handle ingestion time") {
    runWithAssigner(None)

    //5 seconds should be enough to run this test...-
    SinkForLongs.data.head.toDouble shouldBe (System.currentTimeMillis().toDouble +- 5000)
  }

}
