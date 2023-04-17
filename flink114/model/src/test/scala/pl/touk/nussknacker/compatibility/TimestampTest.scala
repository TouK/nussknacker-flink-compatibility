package pl.touk.nussknacker.compatibility

import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.compatibility.common.BaseTimestampTest

class TimestampTest extends BaseTimestampTest with FlinkSpec with Matchers
