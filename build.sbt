name := "hello-akka-typed-scala"

version := "1.0"

scalaVersion := "2.13.0"

lazy val akkaVersion = "2.6.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  // persistence plugin
  "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.2",
  "org.postgresql" % "postgresql" % "42.2.5",
  // testing
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
)

Global / onChangedBuildSource := ReloadOnSourceChanges
