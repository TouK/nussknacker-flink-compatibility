libraryDependencies ++= Seq(
  "com.github.pathikrit" %% "better-files" % "3.9.2",
  "com.lihaoyi"          %% "os-lib"       % "0.10.3",
)

addDependencyTreePlugin
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"  % "2.2.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"  % "2.5.2")
addSbtPlugin("com.typesafe.sbt"   % "sbt-git"       % "1.0.1")
addSbtPlugin("uk.co.randomcoding" % "sbt-git-hooks" % "0.2.0")
