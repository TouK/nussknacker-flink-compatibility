package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import org.apache.flink.api.common.typeutils.CompositeTypeSerializerUtil.IntermediateCompatibilityResult
import org.apache.flink.api.common.typeutils.{CompositeTypeSerializerUtil, TypeSerializer, TypeSerializerSnapshot}

class Flink114CompositeTypeSerializerUtilCompatibilityProvider extends CompositeTypeSerializerUtilCompatibilityProvider {

  override def constructIntermediateCompatibilityResult[T](
      newNestedSerializerSnapshots: Array[TypeSerializer[_]],
      oldNestedSerializerSnapshots: Array[TypeSerializerSnapshot[_]]
  ): IntermediateCompatibilityResult[T] = {
    CompositeTypeSerializerUtil.constructIntermediateCompatibilityResult(
      newNestedSerializerSnapshots,
      oldNestedSerializerSnapshots
    )
  }

}
