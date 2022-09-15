package pl.touk.nussknacker.compatibility

import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.compatibility.common.BaseGenericITSpec
import pl.touk.nussknacker.compatibility.flink111.Flink111Spec

class GenericITSpec extends AnyFunSuite with BaseGenericITSpec with Flink111Spec
