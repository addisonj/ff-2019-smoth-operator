name := "smooth-operator-demo-code"

version := "0.1-SNAPSHOT"

organization := "com.instructure"

scalacOptions += "-Ywarn-unused-import"
ThisBuild / scalaVersion := "2.11.12"

val flinkVersion = "1.7.2"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++=
      Dependencies.flink(flinkVersion)
        ++ Dependencies.aws()
        ++ Dependencies.other
        ++ Dependencies.test(flinkVersion)
  )

mainClass in (Compile, run) := Some("com.instructure.flink.demo.FileReaderDemo")
// stays inside the sbt console when we press "ctrl-c" while a Flink programme executes with "run" or "runMain"
Compile / fork := true
Global / cancelable := true

// make run command include the provided dependencies
Compile / run := Defaults
  .runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner)
  .evaluated

// exclude Scala library from assembly
assembly / assemblyOption := (assembly / assemblyOption).value.copy(includeScala = false)

assemblyMergeStrategy in assembly := {
  case "log4j.properties"                                           => MergeStrategy.last
  case "mozilla/public-suffix-list.txt"                             => MergeStrategy.last
  case PathList("org", "apache", "commons", "beanutils", xs @ _*)   => MergeStrategy.first
  case PathList("org", "apache", "commons", "collections", xs @ _*) => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
