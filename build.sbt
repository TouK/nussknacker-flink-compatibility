import sbt.Keys.*
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.{MergeStrategy, PathList}
import sbtrelease.ReleasePlugin.autoImport.releaseStepCommand
import sbtrelease.ReleaseStateTransformations.*
import utils.{codeVersion, forScalaVersion}

val scala212V = "2.12.10"
val scala213V = "2.13.12"

lazy val supportedScalaVersions = List(scala212V, scala213V)
ThisBuild / scalaVersion := scala212V

val scalaCollectionsCompatV = "2.9.0"

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
val silencerV_2_12 = "1.6.0"
val silencerV      = "1.7.17"

val flink118V            = "1.18.1"
val flinkConnectorKafkaV = "3.2.0"
val sttpV                = "3.8.11"
val kafkaV               = "3.3.1"
val testContainersScalaV = "0.41.0"
val logbackV             = "1.5.12"

val baseVersion = "1.1.0"
ThisBuild / isSnapshot := true

val nussknackerV = settingKey[String]("Nussknacker version")
ThisBuild / nussknackerV := "1.18.0"
ThisBuild / version      := codeVersion(baseVersion, nussknackerV.value, (ThisBuild / isSnapshot).value)

// Global publish settings
ThisBuild / publishTo := {
  if ((ThisBuild / isSnapshot).value)
    Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
  else
    sonatypePublishToBundle.value
}

ThisBuild / publish / skip := true

lazy val root = (project in file("."))
  .enablePlugins(FormatStagedScalaFilesPlugin)
  .aggregate(
    flink118KafkaComponents,
    flink118ManagerCompat,
    flink118ModelCompat,
  )
  .settings(commonSettings)
  .settings(
    Seq(
      name                          := "nussknacker-flink-compatibility",
      // crossScalaVersions must be set to Nil on the aggregating project
      crossScalaVersions            := Nil,
      releasePublishArtifactsAction := PgpKeys.publishSigned.value,
      releaseProcess                := {
        if ((ThisBuild / isSnapshot).value) {
          Seq[ReleaseStep](
            runClean,
            releaseStepCommandAndRemaining("+publishSigned"),
          )
        } else {
          Seq[ReleaseStep](
            checkSnapshotDependencies,
            runClean,
            tagRelease,
            releaseStepCommandAndRemaining("+publishSigned"),
            releaseStepCommand("sonatypeBundleRelease"),
            pushChanges
          )
        }
      }
    )
  )

lazy val commonSettings = Seq(
  organization                                    := "pl.touk.nussknacker",
  resolvers ++=
    Resolver.sonatypeOssRepos("public") ++
      Opts.resolver.sonatypeOssSnapshots :+
      ("confluent" at "https://packages.confluent.io/maven"),
  crossScalaVersions                              := supportedScalaVersions,
  scalacOptions                                   := Seq(
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
  libraryDependencies ++= forScalaVersion(scalaVersion.value) {
    case (2, 12) => Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full))
    case _       => Seq()
  },
  // We can't use addCompilerPlugin because it not support usage of scalaVersion.value
  libraryDependencies += compilerPlugin(
    "com.github.ghik" % "silencer-plugin" % forScalaVersion(scalaVersion.value) {
      case (2, 12) => silencerV_2_12
      case _       => silencerV
    } cross CrossVersion.full
  ),
  libraryDependencies ++= Seq(
    "com.github.ghik" % "silencer-lib" % forScalaVersion(scalaVersion.value) {
      case (2, 12) => silencerV_2_12
      case _       => silencerV
    }                 % Provided cross CrossVersion.full
  ),
  libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionsCompatV,
  assembly / assemblyOption                       := (assembly / assemblyOption).value.withIncludeScala(false).withLevel(Level.Info),
  assembly / assemblyMergeStrategy                := nussknackerAssemblyStrategy,
  assembly / assemblyJarName                      := s"${name.value}-assembly.jar",
  assembly / test                                 := {},
)

lazy val publishSettings = Seq(
  homepage               := Some(url("https://github.com/TouK/nussknacker-flink-compatibility")),
  licenses               := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  publishMavenStyle      := true,
  publish / skip         := false,
  Test / publishArtifact := false,
  pomExtra in Global     := {
    <developers>
        <developer>
          <id>TouK</id>
          <name>TouK</name>
          <url>https://touk.pl</url>
        </developer>
      </developers>
  },
)

lazy val flink118ModelCompat = (project in file("flink118/model"))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "nussknacker-flink-compatibility-1-18-model",
    libraryDependencies ++= Seq(
      "org.apache.flink"     % "flink-statebackend-rocksdb"                       % flink118V          % "provided",
      "pl.touk.nussknacker" %% "nussknacker-default-model"                        % nussknackerV.value,
      "pl.touk.nussknacker" %% "nussknacker-flink-schemed-kafka-components-utils" % nussknackerV.value,
      "pl.touk.nussknacker" %% "nussknacker-flink-base-components"                % nussknackerV.value,
      "pl.touk.nussknacker" %% "nussknacker-flink-base-unbounded-components"      % nussknackerV.value,
      "pl.touk.nussknacker" %% "nussknacker-flink-executor"                       % nussknackerV.value,
      "pl.touk.nussknacker" %% "nussknacker-flink-test-utils"                     % nussknackerV.value % "test" excludeAll (
        ExclusionRule("log4j", "log4j"),
        ExclusionRule("org.slf4j", "slf4j-log4j12"),
        ExclusionRule("org.apache.flink", "flink-scala_2.12"),
      ),
      "pl.touk.nussknacker" %% "nussknacker-kafka-test-utils"                     % nussknackerV.value % "test",
      "org.apache.flink"     % "flink-streaming-java"                             % flink118V          % "test, provided",
      // in normal deployment (from designer) flink-metrics-dropwizard and flink-metrics-dropwizard-core should be replaced in flink-dropwizard-metrics-deps directory in container/distribution
      "org.apache.flink"     % "flink-metrics-dropwizard"                         % flink118V,
      "ch.qos.logback"       % "logback-classic"                                  % logbackV           % "test"
    ),
    dependencyOverrides ++= Seq(
      "org.apache.kafka"  % "kafka-clients" % kafkaV,
      "org.apache.kafka" %% "kafka"         % kafkaV
    ) ++ flinkOverrides(flink118V)
  )
  .dependsOn(flink118KafkaComponents % Test)

lazy val flink118ManagerCompat = (project in file("flink118/manager"))
  .settings(commonSettings)
  .settings(
    name             := "nussknacker-flink-compatibility-1-18-manager",
    libraryDependencies ++= Seq(
      "pl.touk.nussknacker"           %% "nussknacker-flink-manager"          % nussknackerV.value excludeAll (
        ExclusionRule("org.apache.flink", "flink-scala_2.12"),
      ),
      "pl.touk.nussknacker"           %% "nussknacker-http-utils"             % nussknackerV.value   % "provided,test",
      "pl.touk.nussknacker"           %% "nussknacker-scenario-compiler"      % nussknackerV.value   % "provided,test",
      "pl.touk.nussknacker"           %% "nussknacker-deployment-manager-api" % nussknackerV.value   % "provided",
      "org.apache.flink"               % "flink-streaming-java"               % flink118V excludeAll (
        ExclusionRule("log4j", "log4j"),
        ExclusionRule("org.slf4j", "slf4j-log4j12"),
      ),
      "com.dimafeng"                  %% "testcontainers-scala-scalatest"     % testContainersScalaV % "test",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-future"   % sttpV,
      "pl.touk.nussknacker"           %% "nussknacker-flink-test-utils"       % nussknackerV.value   % "test",
      "ch.qos.logback"                 % "logback-classic"                    % logbackV             % "test"
    ),
    dependencyOverrides ++= Seq(
      // For some strange reason, docker client libraries have conflict with schema registry client :/
      "org.glassfish.jersey.core" % "jersey-common"      % "2.22.2",
      // must be the same as used by flink - otherwise it is evicted by version from deployment-manager-api
      "com.typesafe.akka"        %% "akka-actor"         % "2.6.20",
      "org.scala-lang.modules"   %% "scala-java8-compat" % "1.0.2"
    ) ++ flinkOverrides(flink118V),
    Test / Keys.test := (Test / Keys.test)
      .dependsOn(flink118ModelCompat / Compile / assembly)
      .value,
  )

lazy val flink118KafkaComponents = (project in file("flink118/kafka-components"))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name                          := "nussknacker-flink-compatibility-1-18-kafka-components",
    libraryDependencies ++= {
      val nussknackerVersion = nussknackerV.value
      Seq(
        "pl.touk.nussknacker" %% "nussknacker-flink-schemed-kafka-components-utils" % nussknackerVersion,
        "org.apache.flink"     % "flink-core"                                       % flink118V,
        "pl.touk.nussknacker" %% "nussknacker-flink-components-api"                 % nussknackerVersion % "provided",
        "pl.touk.nussknacker" %% "nussknacker-flink-extensions-api"                 % nussknackerVersion % "provided",
        "pl.touk.nussknacker" %% "nussknacker-utils"                                % nussknackerVersion % "provided",
        "pl.touk.nussknacker" %% "nussknacker-components-utils"                     % nussknackerVersion % "provided",
        "org.apache.flink"     % "flink-streaming-java"                             % flink118V          % "provided"
      )
    },
    dependencyOverrides ++= Seq(
      "org.apache.kafka"  % "kafka-clients" % kafkaV,
      "org.apache.kafka" %% "kafka"         % kafkaV
    ) ++ flinkOverrides(flink118V),
    Compile / assembly / artifact := {
      val art = (Compile / assembly / artifact).value
      art.withClassifier(Some("assembly"))
    },
    addArtifact(Compile / assembly / artifact, assembly)
  )

def flinkOverrides(flinkV: String) = {
  val parsedFlinkVersion = VersionNumber(flinkV)
  val shortFlinkVersion  = s"${parsedFlinkVersion._1.get}.${parsedFlinkVersion._2.get}"
  Seq(
    "org.apache.flink" %% "flink-streaming-scala"      % flinkV                                      % "provided",
    "org.apache.flink"  % "flink-streaming-java"       % flinkV                                      % "provided",
    "org.apache.flink"  % "flink-core"                 % flinkV                                      % "provided",
    "org.apache.flink"  % "flink-rpc-akka-loader"      % flinkV                                      % "provided",
    "org.apache.flink" %% "flink-scala"                % flinkV                                      % "provided",
    "org.apache.flink"  % "flink-avro"                 % flinkV                                      % "provided",
    "org.apache.flink"  % "flink-runtime"              % flinkV                                      % "provided",
    "org.apache.flink"  % "flink-test-utils"           % flinkV                                      % "provided",
    "org.apache.flink"  % "flink-statebackend-rocksdb" % flinkV                                      % "provided",
    "org.apache.flink"  % "flink-connector-kafka"      % s"$flinkConnectorKafkaV-$shortFlinkVersion" % "provided",
    "org.apache.flink"  % "flink-metrics-dropwizard"   % flinkV                                      % "test",
  )
}

def nussknackerAssemblyStrategy: String => MergeStrategy = {
  case PathList(ps @ _*) if ps.last == "NumberUtils.class"                             => MergeStrategy.first
  case PathList("org", "apache", "commons", "logging", _ @_*)                          => MergeStrategy.first
  case PathList("javax", "activation", _ @_*)                                          => MergeStrategy.first
  case PathList("javax", "el", xs @ _*)                                                => MergeStrategy.first
  case PathList("javax", "validation", xs @ _*)                                        => MergeStrategy.first
  case PathList("com", "sun", "activation", _ @_*)                                     => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "io.netty.versions.properties"                  => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "mailcap.default"                               => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "mimetypes.default"                             => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "module-info.class"                             => MergeStrategy.first
  case PathList("org", "apache", "commons", "collections", ps)
      if ps.contains("FastHashMap") || ps == "ArrayStack.class" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last.matches("FlinkMetricsProviderForScenario.*.class") => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "MetricUtils.class"                             => MergeStrategy.first
  // Due to swagger-parser dependencies having different schema definitions
  case PathList(ps @ _*) if ps.head == "draftv4" && ps.last == "schema"                => MergeStrategy.first
  case PathList(ps @ _*) if ps.last.matches("CollectionSource.*.class")                => MergeStrategy.first

  case PathList("com", "esotericsoftware", "minlog", "Log.class")        => MergeStrategy.first
  case PathList("com", "esotericsoftware", "minlog", "Log$Logger.class") => MergeStrategy.first

  case x => MergeStrategy.defaultMergeStrategy(x)
}

lazy val printVersion = taskKey[Unit]("print version")

printVersion := {
  val s: TaskStreams = streams.value
  s.log.success(s"Nu version: ${(ThisBuild / version).value}")
}
