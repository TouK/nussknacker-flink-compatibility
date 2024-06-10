import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.{MergeStrategy, PathList}

val scala212V = "2.12.10"

val scalaCollectionsCompatV = "2.9.0"

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
val silencerV_2_12 = "1.6.0"
val silencerV = "1.7.0"

val flink114V = "1.14.5"
val flink116V = "1.16.0"
val currentFlinkV = "1.18.1"
val sttpV = "3.8.11"
val kafkaV = "3.3.1"
val testContainersScalaV = "0.41.0"

ThisBuild / version := "0.1-SNAPSHOT"

// todo: for now we should regularly bump the version until we start publish single "latest" -SNAPSHOT version
val defaultNussknackerV = "1.15.2"

val nussknackerV = {
  val v = sys.env
    .get("NUSSKNACKER_VERSION")
    .filterNot(_.isBlank)
    .getOrElse(defaultNussknackerV)
  println(s"Nussknacker version: $v")
  v
}

def commonSettings(scalaV: String) =
  Seq(
    organization := "pl.touk.nussknacker.flinkcompatibility",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("public"),
      Opts.resolver.sonatypeSnapshots,
      "confluent" at "https://packages.confluent.io/maven",
      "nexus" at sys.env
        .getOrElse("nexus", "https://nexus.touk.pl/nexus/content/groups/public")
    ),
    scalaVersion := scalaV,
    scalacOptions := Seq(
      "-unchecked",
      "-deprecation",
      "-encoding",
      "utf8",
      "-Xfatal-warnings",
      "-feature",
      "-language:postfixOps",
      "-language:existentials",
      "-language:higherKinds",
      "-target:jvm-1.8",
      "-J-Xss4M"
    ),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
    ),
    // We can't use addCompilerPlugin because it not support usage of scalaVersion.value
    libraryDependencies += compilerPlugin(
      "com.github.ghik" % "silencer-plugin" % (CrossVersion
        .partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => silencerV_2_12
        case _             => silencerV
      }) cross CrossVersion.full
    ),
    libraryDependencies ++= Seq(
      "com.github.ghik" % "silencer-lib" % (CrossVersion
        .partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => silencerV_2_12
        case _             => silencerV
      }) % Provided cross CrossVersion.full,
      "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionsCompatV
    ),
    assembly / assemblyOption := (assembly / assemblyOption).value
      .copy(includeScala = false, level = Level.Info),
    assembly / assemblyMergeStrategy := nussknackerAssemblyStrategy,
    assembly / assemblyJarName := s"${name.value}-assembly.jar",
    assembly / test := {}
  )

//Here we use Flink version from Nussknacker, in each compatibility provider it will be overridden.
lazy val commonTest = (project in file("commonTest"))
  .settings(commonSettings(scala212V))
  .settings(
    name := "commonTest",
    libraryDependencies ++= Seq(
      "pl.touk.nussknacker" %% "nussknacker-default-model" % nussknackerV,
      "pl.touk.nussknacker" %% "nussknacker-flink-schemed-kafka-components-utils" % nussknackerV,
      "pl.touk.nussknacker" %% "nussknacker-kafka-test-utils" % nussknackerV,
      "pl.touk.nussknacker" %% "nussknacker-flink-test-utils" % nussknackerV excludeAll (
        ExclusionRule("log4j", "log4j"),
        ExclusionRule("org.slf4j", "slf4j-log4j12"),
        ExclusionRule("org.apache.flink", "flink-scala_2.12"),
      ),
      "pl.touk.nussknacker" %% "nussknacker-flink-executor" % nussknackerV,
      "org.apache.flink" %% "flink-streaming-scala" % currentFlinkV % "provided",
      "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainersScalaV,
      "pl.touk.nussknacker" %% "nussknacker-flink-manager" % nussknackerV excludeAll (
        ExclusionRule("org.apache.flink", "flink-scala_2.12"),
      ),
      "pl.touk.nussknacker" %% "nussknacker-deployment-manager-api" % nussknackerV % "provided",
      "pl.touk.nussknacker" %% "nussknacker-flink-kafka-components" % nussknackerV,
      "pl.touk.nussknacker" %% "nussknacker-flink-base-components" % nussknackerV,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpV,
    ),
    dependencyOverrides ++= Seq(
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
      "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
    )
  )

lazy val flink114ModelCompat = (project in file("flink114/model"))
  .settings(commonSettings(scala212V))
  .settings(flinkSettingsCommonForBefore1_15(flink114V))
  .settings(
    name := "flink114-model",
    libraryDependencies ++= deps(flink114V),
    dependencyOverrides ++= Seq(
      "org.apache.kafka" % "kafka-clients" % kafkaV,
      "org.apache.kafka" %% "kafka" % kafkaV,
    ),
  )
  .dependsOn(commonTest % "test,it")

lazy val flink114ManagerCompat = (project in file("flink114/manager"))
  .settings(commonSettings(scala212V))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(flinkSettingsCommonForBefore1_15(flink114V))
  .settings(
    name := "flink114-manager",
    libraryDependencies ++= managerDeps(flink114V),
    dependencyOverrides ++= Seq(
      //For some strange reason, docker client libraries have conflict with schema registry client :/
      "org.glassfish.jersey.core" % "jersey-common" % "2.22.2",
      // must be the same as used by flink - otherwise it is evicted by version from deployment-manager-api
      "com.typesafe.akka" %% "akka-actor" % "2.6.20",
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
    ),
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test)
      .dependsOn(flink114ModelCompat / Compile / assembly)
      .value,
  )
  .dependsOn(commonTest % "test,it")

lazy val flink116ModelCompat = (project in file("flink116/model"))
  .settings(commonSettings(scala212V))
  .settings(
    name := "flink116-model",
    libraryDependencies ++= deps(flink116V),
    dependencyOverrides ++= Seq(
      "org.apache.kafka" % "kafka-clients" % kafkaV,
      "org.apache.kafka" %% "kafka" % kafkaV,
    ),
  )
  .dependsOn(commonTest % "test,it")

lazy val flink116ManagerCompat = (project in file("flink116/manager"))
  .settings(commonSettings(scala212V))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(
    name := "flink116-manager",
    libraryDependencies ++= managerDeps(flink116V),
    dependencyOverrides ++= Seq(
      //For some strange reason, docker client libraries have conflict with schema registry client :/
      "org.glassfish.jersey.core" % "jersey-common" % "2.22.2",
      // must be the same as used by flink - otherwise it is evicted by version from deployment-manager-api
      "com.typesafe.akka" %% "akka-actor" % "2.6.20",
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
    ),
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test)
      .dependsOn(flink116ModelCompat / Compile / assembly)
      .value,
  )
  .dependsOn(commonTest % "test,it")

def flinkExclusionsForBefore1_15 = Seq(
  "org.apache.flink" % "flink-streaming-java",
  "org.apache.flink" % "flink-statebackend-rocksdb",
  "org.apache.flink" % "flink-connector-kafka",
  "org.apache.flink" % "flink-test-utils"
)

def flinkDependenciesCommonForBefore1_15(version: String) = Seq(
  "org.apache.flink" %% "flink-connector-kafka" % version % "provided",
  "org.apache.flink" % "flink-runtime" % version % "provided",
  "org.apache.flink" %% "flink-test-utils" % version % "provided",
  "org.apache.flink" % "flink-statebackend-rocksdb" % version % "provided"
)

def flinkOverridesCommonForBefore1_15(version: String) =
  flinkDependenciesCommonForBefore1_15(version)

def flinkSettingsCommonForBefore1_15(version: String) = Seq(
  excludeDependencies ++= flinkExclusionsForBefore1_15,
  libraryDependencies ++= flinkDependenciesCommonForBefore1_15(version),
  dependencyOverrides ++= flinkOverrides(version) ++ flinkOverridesCommonForBefore1_15(
    version
  )
)

def managerDeps(version: String) = Seq(
  "pl.touk.nussknacker" %% "nussknacker-flink-manager" % nussknackerV excludeAll (
    ExclusionRule("org.apache.flink", "flink-scala_2.12"),
  ),
  "pl.touk.nussknacker" %% "nussknacker-http-utils" % nussknackerV % "provided,it,test",
  "pl.touk.nussknacker" %% "nussknacker-scenario-compiler" % nussknackerV % "provided,it,test",
  "pl.touk.nussknacker" %% "nussknacker-deployment-manager-api" % nussknackerV % "provided",
  "org.apache.flink" %% "flink-streaming-scala" % version excludeAll (
    ExclusionRule("log4j", "log4j"),
    ExclusionRule("org.slf4j", "slf4j-log4j12"),
  ),
  "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainersScalaV % "it,test",
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpV,
)

def deps(version: String) = Seq(
  "org.apache.flink" %% "flink-streaming-scala" % version % "provided",
  "org.apache.flink" % "flink-statebackend-rocksdb" % version % "provided",
  "pl.touk.nussknacker" %% "nussknacker-default-model" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-base-components" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-base-unbounded-components" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-executor" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-test-utils" % nussknackerV % "test",
  "org.apache.flink" %% "flink-streaming-scala" % version % "test",
)

def flinkOverrides(version: String) = Seq(
  "org.apache.flink" %% "flink-streaming-scala" % version % "provided",
  "org.apache.flink" %% "flink-scala" % version % "provided",
  "org.apache.flink" % "flink-statebackend-rocksdb" % version % "provided",
  "org.apache.flink" % "flink-avro" % version,
  "org.apache.flink" %% "flink-runtime" % version % "provided",
  "org.apache.flink" %% "flink-connector-kafka" % version % "provided",
  "org.apache.flink" %% "flink-test-utils" % version % "test",
  "org.apache.flink" % "flink-metrics-dropwizard" % version % "test",
)

def nussknackerAssemblyStrategy: String => MergeStrategy = {
  case PathList(ps @ _*) if ps.last == "NumberUtils.class" =>
    MergeStrategy.first
  case PathList("org", "apache", "commons", "logging", _ @_*) =>
    MergeStrategy.first
  case PathList("javax", "activation", _ @_*)      => MergeStrategy.first
  case PathList("javax", "el", xs @ _*)            => MergeStrategy.first
  case PathList("javax", "validation", xs @ _*)    => MergeStrategy.first
  case PathList("com", "sun", "activation", _ @_*) => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "io.netty.versions.properties" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "mailcap.default" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "mimetypes.default" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "module-info.class" =>
    MergeStrategy.first
  case PathList("org", "apache", "commons", "collections", ps)
      if ps.contains("FastHashMap") || ps == "ArrayStack.class" =>
    MergeStrategy.first
  case PathList(ps @ _*)
      if ps.last.matches("FlinkMetricsProviderForScenario.*.class") =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "MetricUtils.class" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.head == "draftv4" && ps.last == "schema" =>
    MergeStrategy.first //Due to swagger-parser dependencies having different schema definitions
  case PathList(ps @ _*) if ps.last.matches("CollectionSource.*.class") =>
    MergeStrategy.first

  case PathList("com", "esotericsoftware", "minlog", "Log.class") =>
    MergeStrategy.first
  case PathList("com", "esotericsoftware", "minlog", "Log$Logger.class") =>
    MergeStrategy.first

  case x => MergeStrategy.defaultMergeStrategy(x)
}
