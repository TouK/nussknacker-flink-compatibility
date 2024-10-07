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

val flink116V            = "1.16.0"
val currentFlinkV        = "1.19.1"
val sttpV                = "3.8.11"
val kafkaV               = "3.3.1"
val testContainersScalaV = "0.41.0"

val baseVersion  = "1.0-SNAPSHOT"
// todo: for now we should regularly bump the version until we start publish single "latest" -SNAPSHOT version
val nussknackerV = "1.18.0-staging-2024-10-01-20796-0b0373cb1-SNAPSHOT"
ThisBuild / version := codeVersion(baseVersion, nussknackerV)

lazy val root = (project in file("."))
  .enablePlugins(FormatStagedScalaFilesPlugin)
  .aggregate(
    flink116KafkaComponents,
    flink116ManagerCompat,
    flink116ModelCompat,
    commonTest
  )
  .settings(commonSettings)
  .settings(
    Seq(
      name                          := "nussknacker-flink-compatibility",
      // crossScalaVersions must be set to Nil on the aggregating project
      crossScalaVersions            := Nil,
      releasePublishArtifactsAction := PgpKeys.publishSigned.value,
      releaseProcess                := Seq[ReleaseStep](
//        checkSnapshotDependencies, TODO: temporarily disable for manual testing
        runClean,
        tagRelease,
        releaseStepCommand("+publishSigned"),
        releaseStepCommand("sonatypeBundleRelease"),
        pushChanges
      )
    )
  )

lazy val commonSettings = Seq(
  organization                                    := "pl.touk.nussknacker",
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    Opts.resolver.sonatypeSnapshots,
    "confluent" at "https://packages.confluent.io/maven",
    "nexus" at sys.env
      .getOrElse("nexus", "https://nexus.touk.pl/nexus/content/groups/public")
  ),
  publish / skip                                  := true,
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
  assembly / test                                 := {}
)

lazy val publishSettings = Seq(
  homepage               := Some(url("https://github.com/TouK/nussknacker-flink-compatibility")),
  licenses               := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  publishMavenStyle      := true,
  publish / skip         := false,
  isSnapshot             := baseVersion contains "-SNAPSHOT",
  publishTo              := {
    val defaultNexusUrl = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at defaultNexusUrl + "content/repositories/snapshots")
    else {
      sonatypePublishToBundle.value
    }
  },
  pomExtra in Global     := {
    <developers>
        <developer>
          <id>TouK</id>
          <name>TouK</name>
          <url>https://touk.pl</url>
        </developer>
      </developers>
  },
  Test / publishArtifact := false,
)

//Here we use Flink version from Nussknacker, in each compatibility provider it will be overridden.
lazy val commonTest = (project in file("commonTest"))
  .settings(commonSettings)
  .settings(
    name := "commonTest",
    libraryDependencies ++= Seq(
      "pl.touk.nussknacker"           %% "nussknacker-default-model"                        % nussknackerV,
      "pl.touk.nussknacker"           %% "nussknacker-flink-schemed-kafka-components-utils" % nussknackerV,
      "pl.touk.nussknacker"           %% "nussknacker-kafka-test-utils"                     % nussknackerV,
      "pl.touk.nussknacker"           %% "nussknacker-flink-test-utils"                     % nussknackerV excludeAll (
        ExclusionRule("log4j", "log4j"),
        ExclusionRule("org.slf4j", "slf4j-log4j12"),
        ExclusionRule("org.apache.flink", "flink-scala_2.12"),
      ),
      "pl.touk.nussknacker"           %% "nussknacker-flink-executor"                       % nussknackerV,
      "org.apache.flink"               % "flink-streaming-java"                             % currentFlinkV % "provided",
      "com.dimafeng"                  %% "testcontainers-scala-scalatest"                   % testContainersScalaV,
      "pl.touk.nussknacker"           %% "nussknacker-flink-manager"                        % nussknackerV excludeAll (
        ExclusionRule("org.apache.flink", "flink-scala_2.12"),
      ),
      "pl.touk.nussknacker"           %% "nussknacker-deployment-manager-api"               % nussknackerV  % "provided",
      "pl.touk.nussknacker"           %% "nussknacker-flink-base-components"                % nussknackerV,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-future"                 % sttpV
    ),
    dependencyOverrides ++= Seq(
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
      "org.scala-lang.modules" %% "scala-xml"          % "2.1.0"
    )
  )
  .dependsOn(flink116KafkaComponents)

lazy val flink116ModelCompat = (project in file("flink116/model"))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "nussknacker-flink-1-16-model",
    libraryDependencies ++= deps(flink116V),
    dependencyOverrides ++= Seq(
      "org.apache.kafka"  % "kafka-clients" % kafkaV,
      "org.apache.kafka" %% "kafka"         % kafkaV
    ) ++ flinkOverrides(flink116V)
  )
  .dependsOn(commonTest % Test)

lazy val flink116ManagerCompat = (project in file("flink116/manager"))
  .settings(commonSettings)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(
    name                        := "nussknacker-flink-1-16-manager",
    libraryDependencies ++= managerDeps(flink116V),
    dependencyOverrides ++= Seq(
      // For some strange reason, docker client libraries have conflict with schema registry client :/
      "org.glassfish.jersey.core" % "jersey-common"      % "2.22.2",
      // must be the same as used by flink - otherwise it is evicted by version from deployment-manager-api
      "com.typesafe.akka"        %% "akka-actor"         % "2.6.20",
      "org.scala-lang.modules"   %% "scala-java8-compat" % "1.0.2"
    ),
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test)
      .dependsOn(flink116ModelCompat / Compile / assembly)
      .value,
  )
  .dependsOn(commonTest % IntegrationTest)

lazy val flink116KafkaComponents = (project in file("flink116/kafka-components"))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "nussknacker-flink-1-16-kafka-components",
    libraryDependencies ++= {
      Seq(
        "pl.touk.nussknacker" %% "nussknacker-flink-schemed-kafka-components-utils" % nussknackerV,
        "pl.touk.nussknacker" %% "nussknacker-flink-components-api"                 % nussknackerV % "provided",
        "pl.touk.nussknacker" %% "nussknacker-flink-extensions-api"                 % nussknackerV % "provided",
        "pl.touk.nussknacker" %% "nussknacker-utils"                                % nussknackerV % "provided",
        "pl.touk.nussknacker" %% "nussknacker-components-utils"                     % nussknackerV % "provided",
        "org.apache.flink"     % "flink-streaming-java"                             % flink116V    % "provided"
      )
    },
    dependencyOverrides ++= Seq(
      "org.apache.kafka"  % "kafka-clients" % kafkaV,
      "org.apache.kafka" %% "kafka"         % kafkaV
    )
  )

def managerDeps(version: String) = Seq(
  "pl.touk.nussknacker"           %% "nussknacker-flink-manager"          % nussknackerV excludeAll (
    ExclusionRule("org.apache.flink", "flink-scala_2.12"),
  ),
  "pl.touk.nussknacker"           %% "nussknacker-http-utils"             % nussknackerV         % "provided,it,test",
  "pl.touk.nussknacker"           %% "nussknacker-scenario-compiler"      % nussknackerV         % "provided,it,test",
  "pl.touk.nussknacker"           %% "nussknacker-deployment-manager-api" % nussknackerV         % "provided",
  "org.apache.flink"               % "flink-streaming-java"               % version excludeAll (
    ExclusionRule("log4j", "log4j"),
    ExclusionRule("org.slf4j", "slf4j-log4j12"),
  ),
  "com.dimafeng"                  %% "testcontainers-scala-scalatest"     % testContainersScalaV % "it,test",
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-future"   % sttpV,
)

def deps(version: String) = Seq(
  "org.apache.flink"     % "flink-statebackend-rocksdb"                       % version      % "provided",
  "pl.touk.nussknacker" %% "nussknacker-default-model"                        % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-schemed-kafka-components-utils" % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-base-components"                % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-base-unbounded-components"      % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-executor"                       % nussknackerV,
  "pl.touk.nussknacker" %% "nussknacker-flink-test-utils"                     % nussknackerV % "test",
  "org.apache.flink"     % "flink-streaming-java"                             % version      % "test, provided",
  // in normal deployment (from designer) flink-metrics-dropwizard and flink-metrics-dropwizard-core should be replaced in flink-dropwizard-metrics-deps directory in container/distribution
  "org.apache.flink"     % "flink-metrics-dropwizard"                         % version,
)

def flinkOverrides(version: String) = Seq(
  "org.apache.flink" %% "flink-streaming-scala"      % version % "provided",
  "org.apache.flink"  % "flink-streaming-java"       % version % "provided",
  "org.apache.flink"  % "flink-core"                 % version % "provided",
  "org.apache.flink"  % "flink-rpc-akka-loader"      % version % "provided",
  "org.apache.flink" %% "flink-scala"                % version % "provided",
  "org.apache.flink"  % "flink-avro"                 % version % "provided",
  "org.apache.flink"  % "flink-runtime"              % version % "provided",
  "org.apache.flink"  % "flink-test-utils"           % version % "provided",
  "org.apache.flink"  % "flink-statebackend-rocksdb" % version % "provided",
  "org.apache.flink"  % "flink-connector-kafka"      % version % "provided",
  "org.apache.flink"  % "flink-metrics-dropwizard"   % version % "test",
)

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
