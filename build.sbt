import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.{MergeStrategy, PathList}

val scalaV = "2.12.10"

version in ThisBuild := "0.1-SNAPSHOT"

val nussknackerV = "2020-09-28-15-13-staging-41d711807ac4c9ab845040f15dd5def28086759d-SNAPSHOT"

val scalaTestV = "3.0.3"

val commonSettings =
  Seq(
    organization := "pl.touk.nussknacker.flinkcompatibility",
    resolvers ++= Seq(
      "Sonatype snaphots" at "https://oss.sonatype.org/content/groups/public/",
      "confluent" at "https://packages.confluent.io/maven",
      "nexus" at sys.env.getOrElse("nexus", "https://nexus.touk.pl/nexus/content/groups/public")
    ),
    scalaVersion := scalaV,
    scalacOptions := Seq(
      "-unchecked",
      "-deprecation",
      "-encoding", "utf8",
      "-Xfatal-warnings",
      "-feature",
      "-language:postfixOps",
      "-language:existentials",
      "-language:higherKinds",
      "-target:jvm-1.8",
      "-J-Xss4M"
    ),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, level = Level.Info),
    assemblyMergeStrategy in assembly := nussknackerAssemblyStrategy,
    assemblyJarName in assembly := s"${name.value}-assembly.jar"
  )

val flink19V = "1.9.2"

lazy val flink19ModelCompat = (project in file("flink19/model")).
  settings(commonSettings).
  settings(
    name := "flink19-model",
    libraryDependencies ++= deps(flink19V),
    dependencyOverrides ++= flinkOverrides(flink19V) ++ Seq(
      //???
      "org.apache.kafka" % "kafka-clients" % "2.2.0"
    )
  )

lazy val flink19ManagerCompat = (project in file("flink19/manager")).
  settings(commonSettings).
  settings(
    name := "flink19-manager",
    libraryDependencies ++= managerDeps(flink19V),
    dependencyOverrides ++= flinkOverrides(flink19V) ++ Seq(
      //???
      "org.apache.kafka" % "kafka-clients" % "2.2.0"
    )
  )

def managerDeps(version: String) = Seq(
  "pl.touk.nussknacker" %% "nussknacker-flink-manager" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-http-utils" % nussknackerV % "provided",
  "pl.touk.nussknacker" %% "nussknacker-interpreter" % nussknackerV % "provided",
  "org.apache.flink" %% "flink-streaming-scala" % version excludeAll(
    ExclusionRule("log4j", "log4j"),
    ExclusionRule("org.slf4j", "slf4j-log4j12")
  ),
  //TODO: integrations test with docker
  //"com.whisk" %% "docker-testkit-scalatest" % "0.9.0" % "it,test",
  //"com.whisk" %% "docker-testkit-impl-spotify" % "0.9.0" % "it,test"
)

def deps(version: String) = Seq(
  "org.apache.flink" %% "flink-streaming-scala" % version % "provided",
  "pl.touk.nussknacker" %% "nussknacker-generic-model" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-kafka-test-util" % nussknackerV % "test",
  "pl.touk.nussknacker" %% "nussknacker-flink-test-util" % nussknackerV % "test",
  "org.apache.flink" %% "flink-streaming-scala" % version % "test",
)

def flinkOverrides(version: String) = Seq(
  "org.apache.flink" %% "flink-streaming-scala" % version % "provided",
  "org.apache.flink" %% "flink-statebackend-rocksdb" % version % "provided",
  "org.apache.flink" % "flink-avro" % version,
  "org.apache.flink" %% "flink-runtime" % version % "provided",
  "org.apache.flink" %% "flink-connector-kafka" % version % "provided",
  "org.apache.flink" %% "flink-test-utils" % version % "test",
  "org.apache.flink" % "flink-metrics-dropwizard" % version % "test")


def nussknackerAssemblyStrategy: String => MergeStrategy = {
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first
  case PathList("org", "apache", "commons", "logging", _@_*) => MergeStrategy.first
  case PathList("javax", "activation", _@_*) => MergeStrategy.first
  case PathList("javax", "el", xs@_*) => MergeStrategy.first
  case PathList("javax", "validation", xs@_*) => MergeStrategy.first
  case PathList("com", "sun", "activation", _@_*) => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "mailcap.default" => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "mimetypes.default" => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "module-info.class" => MergeStrategy.first
  case PathList("org", "apache", "commons", "collections", ps) if ps.contains("FastHashMap") || ps == "ArrayStack.class" => MergeStrategy.first
  case x => MergeStrategy.defaultMergeStrategy(x)
}
