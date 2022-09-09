package pl.touk.nussknacker.compatibility

import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.compatibility.common.BaseTimestampTest
import pl.touk.nussknacker.compatibility.flink111.Flink111Spec

class TimestampTest extends BaseTimestampTest with Flink111Spec with Matchers
