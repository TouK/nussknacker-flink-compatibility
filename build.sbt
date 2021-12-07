import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.{MergeStrategy, PathList}

val scala212V = "2.12.10"

val scalaCollectionsCompatV = "2.1.6"

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
val silencerV_2_12 = "1.6.0"
val silencerV = "1.7.0"

ThisBuild / version := "0.1-SNAPSHOT"

val nussknackerV = "1.1.0"

val scalaTestV = "3.0.3"

def commonSettings(scalaV: String) =
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
    // We can't use addCompilerPlugin because it not support usage of scalaVersion.value
    libraryDependencies += compilerPlugin("com.github.ghik" % "silencer-plugin" % (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => silencerV_2_12
      case _             => silencerV
    }) cross CrossVersion.full),
    libraryDependencies ++= Seq(
      "com.github.ghik" % "silencer-lib" % (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => silencerV_2_12
        case _             => silencerV
      }) % Provided cross CrossVersion.full,
      "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionsCompatV
    ),
    assembly / assemblyOption := (assembly / assemblyOption).value.copy(includeScala = false, level = Level.Info),
    assembly / assemblyMergeStrategy := nussknackerAssemblyStrategy,
    assembly / assemblyJarName := s"${name.value}-assembly.jar",
    assembly / test := {}
  )

val flink111V = "1.11.3"
val flink113V = "1.13.3"
val currentFlinkV = "1.14.0"
val sttpV = "2.2.9"

//Here we use Flink version from Nussknacker, in each compatibility provider it will be overridden.
lazy val commonTest = (project in file("commonTest")).
  settings(commonSettings(scala212V)).
  settings(
    name := "commonTest",
    libraryDependencies ++= Seq(
      "pl.touk.nussknacker" %% "nussknacker-generic-model" % nussknackerV,
      "pl.touk.nussknacker" %% "nussknacker-kafka-test-util" % nussknackerV,
      "pl.touk.nussknacker" %% "nussknacker-flink-test-util" % nussknackerV,
      "org.apache.flink" %% "flink-streaming-scala" % currentFlinkV % "provided",
    )
  )

lazy val flink111ModelCompat = (project in file("flink111/model")).
  settings(commonSettings(scala212V)).
  settings(flinkExclusionsForBefore1_14).
  settings(
    name := "flink111-model",
    libraryDependencies ++= deps(flink111V),
    dependencyOverrides ++= flinkOverrides(flink111V) ++ Seq(
      //???
      "org.apache.kafka" % "kafka-clients" % "2.4.1",
    )
  ).dependsOn(commonTest % "test")

lazy val flink111ManagerCompat = (project in file("flink111/manager")).
  settings(commonSettings(scala212V)).
  configs(IntegrationTest).
  settings(Defaults.itSettings).
  settings(flinkExclusionsForBefore1_14).
  settings(
    name := "flink111-manager",
    libraryDependencies ++= managerDeps(flink111V),
    dependencyOverrides ++= flinkOverrides(flink111V) ++ Seq(
      //For some strange reason, docker client libraries have conflict with schema registry client :/
      "org.glassfish.jersey.core" % "jersey-common" % "2.22.2",
      "org.apache.kafka" % "kafka-clients" % "2.4.1"
    ),
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test).dependsOn(
      flink111ModelCompat / Compile / assembly
    ).value,
  ).dependsOn(commonTest % "test")

def managerDeps(version: String) = Seq(
  "pl.touk.nussknacker" %% "nussknacker-flink-manager" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-http-utils" % nussknackerV % "provided,it,test",
  "pl.touk.nussknacker" %% "nussknacker-interpreter" % nussknackerV % "provided,it,test",
  "pl.touk.nussknacker" %% "nussknacker-deployment-manager-api" % nussknackerV,

  "pl.touk.nussknacker" %% "nussknacker-kafka-test-util" % nussknackerV % "it,test",
  "org.apache.flink" %% "flink-streaming-scala" % version excludeAll(
    ExclusionRule("log4j", "log4j"),
    ExclusionRule("org.slf4j", "slf4j-log4j12")
  ),
  "com.whisk" %% "docker-testkit-scalatest" % "0.9.0" % "it,test",
  "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.0" % "it,test",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV,
)

val flinkExclusionsForBefore1_14 = Seq(
  excludeDependencies ++= List(
    "org.apache.flink" % "flink-runtime",
    "org.apache.flink" % "flink-queryable-state-runtime"
  )
)

def deps(version: String) = Seq(
  "org.apache.flink" %% "flink-streaming-scala" % version % "provided",
  "org.apache.flink" %% "flink-statebackend-rocksdb" % version % "provided",
  "pl.touk.nussknacker" %% "nussknacker-generic-model" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-base-components" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-engine" % nussknackerV,

  "pl.touk.nussknacker" %% "nussknacker-kafka-test-util" % nussknackerV % "test",
  "pl.touk.nussknacker" %% "nussknacker-flink-test-util" % nussknackerV % "test",
  "org.apache.flink" %% "flink-streaming-scala" % version % "test",
)

def testUtilDeps(version: String) = Seq(
  "pl.touk.nussknacker" %% "nussknacker-flink-test-util" % nussknackerV
)

def flinkOverrides(version: String) = Seq(
  "org.apache.flink" %% "flink-streaming-scala" % version % "provided",
  "org.apache.flink" %% "flink-statebackend-rocksdb" % version % "provided",
  "org.apache.flink" % "flink-avro" % version,
  "org.apache.flink" %% "flink-runtime" % version % "provided",
  "org.apache.flink" %% "flink-connector-kafka" % version % "provided",
  "org.apache.flink" %% "flink-test-utils" % version % "test",
  "org.apache.flink" % "flink-metrics-dropwizard" % version % "test",
)


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
  case PathList(ps@_*) if ps.last == "FlinkMetricsProviderForScenario.class" => MergeStrategy.first

  case x => MergeStrategy.defaultMergeStrategy(x)
}
