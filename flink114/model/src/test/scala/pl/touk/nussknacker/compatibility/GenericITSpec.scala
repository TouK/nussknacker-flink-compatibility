package pl.touk.nussknacker.compatibility

import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.compatibility.common.BaseGenericITSpec
import pl.touk.nussknacker.compatibility.flink114.Flink114Spec

class GenericITSpec extends AnyFunSuite with BaseGenericITSpec with Flink114Spec
