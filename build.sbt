import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.{MergeStrategy, PathList}

val scala212V = "2.12.10"

val scalaCollectionsCompatV = "2.3.2"

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
val silencerV_2_12 = "1.6.0"
val silencerV = "1.7.0"

val flink111V = "1.11.3"
val flink114V = "1.14.5"
val currentFlinkV = "1.15.2"
val sttpV = "2.2.9"
val kafkaV = "2.8.1"

ThisBuild / version := "0.1-SNAPSHOT"

//val defaultNussknackerV = "1.5.0" 
val defaultNussknackerV = "1.6.0-staging-2022-09-12-9438-7b56cf6041a1a5308e848e1b6829c3a6043f0eaa-SNAPSHOT"

val nussknackerV = {
  val v = sys.env.get("NUSSKNACKER_VERSION").filterNot(_.isBlank).getOrElse(defaultNussknackerV)
  println(s"Nussknacker version: $v")
  v
}

val scalaTestV = "3.0.8"

def commonSettings(scalaV: String) =
  Seq(
    organization := "pl.touk.nussknacker.flinkcompatibility",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("public"),
      Opts.resolver.sonatypeSnapshots,
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

//Here we use Flink version from Nussknacker, in each compatibility provider it will be overridden.
lazy val commonTest = (project in file("commonTest")).
  settings(commonSettings(scala212V)).
  settings(
    name := "commonTest",
    libraryDependencies ++= Seq(
      "pl.touk.nussknacker" %% "nussknacker-default-model" % nussknackerV,
      "pl.touk.nussknacker" %% "nussknacker-flink-schemed-kafka-components-utils" % nussknackerV exclude("org.apache.flink", "flink-streaming-java"),
      "pl.touk.nussknacker" %% "nussknacker-kafka-test-utils" % nussknackerV,
      "pl.touk.nussknacker" %% "nussknacker-flink-test-utils" % nussknackerV exclude("org.apache.flink", "flink-streaming-java"),
      "pl.touk.nussknacker" %% "nussknacker-flink-executor" % nussknackerV exclude("org.apache.flink", "flink-streaming-java"),
    )
  )

//lazy val flink111ModelCompat = (project in file("flink111/model")).
//  settings(commonSettings(scala212V)).
//  settings(flinkExclusionsForBefore1_14).
//  settings(
//    name := "flink111-model",
//    libraryDependencies ++= deps(flink111V),
//    dependencyOverrides ++= flinkOverrides(flink111V) ++ Seq(
//      //???
//      "org.apache.kafka" % "kafka-clients" % kafkaV
//    )
//  ).dependsOn(commonTest % "test")
//
//lazy val flink111ManagerCompat = (project in file("flink111/manager")).
//  settings(commonSettings(scala212V)).
//  configs(IntegrationTest).
//  settings(Defaults.itSettings).
//  settings(flinkExclusionsForBefore1_14).
//  settings(
//    name := "flink111-manager",
//    libraryDependencies ++= managerDeps(flink111V),
//    dependencyOverrides ++= flinkOverrides(flink111V) ++ Seq(
//      //For some strange reason, docker client libraries have conflict with schema registry client :/
//      "org.glassfish.jersey.core" % "jersey-common" % "2.22.2",
//      "org.apache.kafka" % "kafka-clients" % kafkaV,
//      // must be the same as used by flink - otherwise it is evicted by version from deployment-manager-api
//      "com.typesafe.akka" %% "akka-actor" % "2.5.21"
//    ),
//    IntegrationTest / Keys.test := (IntegrationTest / Keys.test).dependsOn(
//      flink111ModelCompat / Compile / assembly
//    ).value,
//  ).dependsOn(commonTest % "test")


lazy val flink114ModelCompat = (project in file("flink114/model")).
  settings(commonSettings(scala212V)).
  settings(
    name := "flink114-model",
    libraryDependencies ++= deps(flink114V),
    dependencyOverrides ++= flinkOverrides(flink114V) ++ Seq(
      //???
      "org.apache.kafka" % "kafka-clients" % kafkaV
    )
  ).dependsOn(commonTest % "test")

lazy val flink114ManagerCompat = (project in file("flink114/manager")).
  settings(commonSettings(scala212V)).
  configs(IntegrationTest).
  settings(Defaults.itSettings).
  settings(
    name := "flink114-manager",
    libraryDependencies ++= managerDeps(flink114V),
    dependencyOverrides ++= flinkOverrides(flink114V) ++ Seq(
      //For some strange reason, docker client libraries have conflict with schema registry client :/
      "org.glassfish.jersey.core" % "jersey-common" % "2.22.2",
      "org.apache.kafka" % "kafka-clients" % kafkaV,
      // must be the same as used by flink - otherwise it is evicted by version from deployment-manager-api
      "com.typesafe.akka" %% "akka-actor" % "2.5.21"
    ),
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test).dependsOn(
      flink114ModelCompat / Compile / assembly
    ).value,
  ).dependsOn(commonTest % "test")


def managerDeps(version: String) = Seq(
  "pl.touk.nussknacker" %% "nussknacker-flink-manager" % nussknackerV exclude("org.apache.flink", "flink-streaming-java"),
  "pl.touk.nussknacker" %% "nussknacker-http-utils" % nussknackerV % "provided,it,test",
  "pl.touk.nussknacker" %% "nussknacker-interpreter" % nussknackerV % "provided,it,test",
  "pl.touk.nussknacker" %% "nussknacker-deployment-manager-api" % nussknackerV % "provided",
  "pl.touk.nussknacker" %% "nussknacker-kafka-test-utils" % nussknackerV % "it,test",

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
  "pl.touk.nussknacker" %% "nussknacker-default-model" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-kafka-components" % nussknackerV exclude("org.apache.flink", "flink-streaming-java"),
  "pl.touk.nussknacker" %% "nussknacker-flink-base-components" % nussknackerV exclude("org.apache.flink", "flink-streaming-java"),
  "pl.touk.nussknacker" %% "nussknacker-flink-executor" % nussknackerV exclude("org.apache.flink", "flink-streaming-java"),

  "pl.touk.nussknacker" %% "nussknacker-kafka-test-utils" % nussknackerV % "test",
  "pl.touk.nussknacker" %% "nussknacker-flink-test-utils" % nussknackerV % "test" exclude("org.apache.flink", "flink-streaming-java"),
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
  case PathList(ps@_*) if ps.last.matches("FlinkMetricsProviderForScenario.*.class") => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "MetricUtils.class" => MergeStrategy.first
  case PathList(ps@_*) if ps.head == "draftv4" && ps.last == "schema" => MergeStrategy.first //Due to swagger-parser dependencies having different schema definitions


  case x => MergeStrategy.defaultMergeStrategy(x)
}
