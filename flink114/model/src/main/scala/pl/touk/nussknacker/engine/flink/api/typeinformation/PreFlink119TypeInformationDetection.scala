package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.{CompositeTypeSerializerUtil, TypeSerializer, TypeSerializerSnapshot}
import pl.touk.nussknacker.engine.process.typeinformation.BaseTypingResultAwareTypeInformationDetection

class PreFlink119TypeInformationDetection
    extends BaseTypingResultAwareTypeInformationDetection
    with CustomTypeInformationDetection {

  override protected def constructIntermediateCompatibilityResult(
      newNestedSerializers: Array[TypeSerializer[_]],
      oldNestedSerializerSnapshots: Array[TypeSerializerSnapshot[_]]
  ): CompositeTypeSerializerUtil.IntermediateCompatibilityResult[Nothing] =
    CompositeTypeSerializerUtil.constructIntermediateCompatibilityResult(
      newNestedSerializers,
      oldNestedSerializerSnapshots
    )
}
