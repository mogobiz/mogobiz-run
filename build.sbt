import spray.revolver.RevolverPlugin.Revolver

import AssemblyKeys._

assemblySettings

jarName in assembly := "mogobiz-run.jar"

mainClass in assembly := Some("com.mogobiz.Boot")

test in assembly := {}

organization := "com.mogobiz"

version := "0.0.1-SNAPSHOT"

logLevel in Global := Level.Warn

crossScalaVersions := Seq("2.11.1")

scalaVersion := "2.11.1"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")


resolvers += "BoneCP Repository" at "http://jolbox.com/bonecp/downloads/maven"

resolvers += Resolver.sonatypeRepo("releases")

resolvers += "spray repo" at "http://repo.spray.io/"

resolvers += "ebiz repo" at "http://art.ebiznext.com/artifactory/libs-release-local"

resolvers += "scribe-java-mvn-repo" at "https://raw.github.com/fernandezpablo85/scribe-java/mvn-repo"

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"


val akkaV = "2.3.3"

val sprayV = "1.3.1"

val jacksonV = "2.4.0-rc2"

val scalikeV = "2.0.5"

val elastic4sV = "1.2.1.3"

val json4sV = "3.2.9"

libraryDependencies ++= Seq(
  "postgresql" % "postgresql" % "9.1-901.jdbc4",
  "mysql" % "mysql-connector-java" % "5.1.12",
  "com.h2database" % "h2" % "1.4.177" % "test",
  "joda-time" % "joda-time" % "2.3",
  "org.joda" % "joda-convert" % "1.7",
  "org.scalikejdbc" %% "scalikejdbc" % scalikeV,
  "org.scalikejdbc" %% "scalikejdbc-config" % scalikeV,
  "org.scalikejdbc" %% "scalikejdbc-interpolation" % scalikeV,
  "org.scalikejdbc" %% "scalikejdbc-test" % scalikeV % "test",
  //"com.typesafe.scala-logging" %% "scala-logging" % "3.0.0",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "io.spray" %% "spray-can" % sprayV,
  "io.spray" %% "spray-client" % sprayV,
  "io.spray" %% "spray-routing" % sprayV,
  "io.spray" %% "spray-testkit" % sprayV,
  "io.spray" %% "spray-http" % sprayV,
  "io.spray" %% "spray-httpx" % sprayV,
  "io.spray" %% "spray-util" % sprayV,
  "org.json4s" %% "json4s-native" % json4sV,
  "org.json4s" %% "json4s-jackson" % json4sV,
  "org.json4s"          %%  "json4s-ext" % json4sV,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonV,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonV,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonV,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "com.sksamuel.elastic4s" %% "elastic4s" % elastic4sV,
  "com.google.zxing" % "core" % "1.7",
  "org.specs2" %% "specs2" % "2.3.13" % "test",
  "org.scala-lang.modules" %% "scala-async" % "0.9.2" % "test"
)
//    "commons-codec" % "commons-codec" % "1.9",
//    "com.google.gdata" % "core" % "1.0",

seq(Revolver.settings: _*)

mainClass in Revolver.reStart := Some("com.mogobiz.Rest")

publishTo := {
  val nexus = "http://art.ebiznext.com/artifactory/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "libs-snapshot-local")
  else
    Some("releases" at nexus + "libs-release-local")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishArtifact in(Compile, packageSrc) := false

publishArtifact in(Test, packageSrc) := false
