import sbt._

object Dependencies {

  def flink(flinkVersion: String): Seq[ModuleID] = Seq(
    "org.apache.flink" %% "flink-connector-filesystem" % flinkVersion,
    "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkVersion,
    "org.apache.flink" %% "flink-table"                % flinkVersion,
    "org.apache.flink" %% "flink-scala"                % flinkVersion % "provided",
    "org.apache.flink" %% "flink-streaming-scala"      % flinkVersion % "provided",
    "org.apache.flink" % "flink-s3-fs-hadoop"          % flinkVersion % "provided"
  )

  def aws(awsVersion: String = "1.11.271"): Seq[ModuleID] = Seq(
    "com.amazonaws" % "aws-java-sdk-core"     % awsVersion,
    "com.amazonaws" % "aws-java-sdk-s3"       % awsVersion
  )

  val other = Seq(
    "org.json4s"      %% "json4s-jackson"      % "3.4.2"
  )

  def test(flinkVersion: String): Seq[ModuleID] = Seq(
    "org.slf4j"       % "slf4j-log4j12"             % "1.7.5" % "provided",
    "org.scalatest"   %% "scalatest"                % "3.0.5" % "test",
    "org.scalamock"   %% "scalamock"                % "4.1.0" % "test",
    // allow us to run the web UI when running tests
    "org.apache.flink" %% "flink-runtime-web" % flinkVersion % "provided",
    // flink deps that include lots of useful test utilities
    "org.apache.flink" %% "flink-test-utils"         % flinkVersion % "test",
    "org.apache.flink" %% "flink-examples-streaming" % flinkVersion % "test",
    ("org.apache.flink" %% "flink-streaming-java" % flinkVersion % "test")
      .classifier("tests")
      .withSources()
      .withJavadoc(),
    ("org.apache.flink" %% "flink-runtime" % flinkVersion % "test")
      .classifier("tests")
      .withSources()
      .withJavadoc(),
    "org.mockito" % "mockito-all" % "1.10.19" % "test"
  )
}
