package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.spel

object SampleProcess {

  import spel.Implicits._

  def prepareProcess(id: String, parallelism: Option[Int] = None) : EspProcess = {
    val baseProcessBuilder = EspProcessBuilder.id(id)
    parallelism.map(baseProcessBuilder.parallelism).getOrElse(baseProcessBuilder)
      .exceptionHandler()
      .source("startProcess", "kafka-json", "topic" -> "'transactions'")
      .filter("nightFilter", "true", endWithMessage("endNight", "Odrzucenie noc"))
      .emptySink("dead-end", "dead-end")
  }

  def kafkaProcess(id: String, topic: String) : EspProcess = {
    EspProcessBuilder
      .id(id)
      .exceptionHandler()
      .source("startProcess", "kafka-json", "topic" -> s"'$topic'")
      .sink("end", "#input", "kafka-json", "topic" -> s"'output-$id'")
  }

  private def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
      .emptySink("end" + idSuffix, "dead-end")
  }

}
