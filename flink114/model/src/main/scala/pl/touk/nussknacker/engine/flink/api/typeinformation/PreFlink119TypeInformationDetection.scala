package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.{CompositeTypeSerializerUtil, TypeSerializer, TypeSerializerSnapshot}
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

class PreFlink119TypeInformationDetection
  extends TypingResultAwareTypeInformationDetection {

  override protected def constructIntermediateCompatibilityResult(
                                                                   newNestedSerializers: Array[TypeSerializer[_]],
                                                                   oldNestedSerializerSnapshots: Array[TypeSerializerSnapshot[_]]
                                                                 ): CompositeTypeSerializerUtil.IntermediateCompatibilityResult[Nothing] =
    CompositeTypeSerializerUtil.constructIntermediateCompatibilityResult(
      newNestedSerializers,
      oldNestedSerializerSnapshots
    )

  override def priority: Int = 0
}
