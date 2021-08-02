package pl.touk.nussknacker.compatibility

import org.scalatest.Matchers
import pl.touk.nussknacker.compatibility.common.BaseTimestampTest
import pl.touk.nussknacker.compatibility.flink19.Flink19Spec

class TimestampTest extends BaseTimestampTest with Flink19Spec with Matchers
