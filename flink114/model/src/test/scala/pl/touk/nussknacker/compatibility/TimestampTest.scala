package pl.touk.nussknacker.compatibility

import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.compatibility.common.BaseTimestampTest
import pl.touk.nussknacker.compatibility.flink114.Flink114Spec

class TimestampTest extends BaseTimestampTest with Flink114Spec with Matchers
