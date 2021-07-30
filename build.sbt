import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.{MergeStrategy, PathList}

val scala211V = "2.11.12"
val scala212V = "2.12.10"

val scalaCollectionsCompatV = "2.1.6"

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
val silencerV_2_12 = "1.6.0"
val silencerV = "1.7.0"

version in ThisBuild := "0.1-SNAPSHOT"

val nussknackerV = "0.5.0-preview_flink_1_12-2021-07-29-3274-23ba0b0918ab2c878551db77ef9d27748a9b8c28-SNAPSHOT"

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
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, level = Level.Info),
    assemblyMergeStrategy in assembly := nussknackerAssemblyStrategy,
    assemblyJarName in assembly := s"${name.value}-assembly.jar"
  )

val flink16V = "1.6.4"
val flink19V = "1.9.2"
val flink111V = "1.11.3"
val currentFlinkV = "1.13.1"

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

lazy val flink19ModelCompat = (project in file("flink19/model")).
  settings(commonSettings(scala212V)).
  settings(
    name := "flink19-model",
    libraryDependencies ++= deps(flink19V),
    dependencyOverrides ++= flinkOverrides(flink19V) ++ Seq(
      //???
      "org.apache.kafka" % "kafka-clients" % "2.4.1"
    )
  ).dependsOn(commonTest % "test")

lazy val flink111ModelCompat = (project in file("flink111/model")).
  settings(commonSettings(scala212V)).
  settings(
    name := "flink111-model",
    libraryDependencies ++= deps(flink111V),
    dependencyOverrides ++= flinkOverrides(flink111V) ++ Seq(
      //???
      "org.apache.kafka" % "kafka-clients" % "2.4.1"
    )
  ).dependsOn(commonTest % "test")

lazy val flink19ManagerCompat = (project in file("flink19/manager")).
  settings(commonSettings(scala212V)).
  settings(
    name := "flink19-manager",
    libraryDependencies ++= managerDeps(flink19V),
    dependencyOverrides ++= flinkOverrides(flink19V) ++ Seq(
      //???
      "org.apache.kafka" % "kafka-clients" % "2.4.1"
    )
  )

lazy val flink16TestUtilCompat = (project in file("flink16/test-util")).
  settings(commonSettings(scala211V)).
  settings(
    name := "flink16-test-util",
    libraryDependencies ++= testUtilDeps(flink16V),
    dependencyOverrides ++= flinkOverrides(flink16V)
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
  "org.apache.flink" %% "flink-statebackend-rocksdb" % version % "provided",
  "pl.touk.nussknacker" %% "nussknacker-generic-model" % nussknackerV,
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
