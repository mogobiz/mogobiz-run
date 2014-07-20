import spray.revolver.RevolverPlugin.Revolver

organization  := "com.mogobiz"

version       := "0.1"

scalaVersion  := "2.10.3"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

val akkaV = "2.2.3" //2.3.3

val sprayV = "1.2.0" //1.3.1

val json4sV = "3.2.6"

val scalikeV = "1.7.7"

val elastic4sV =  "1.2.1.3"

libraryDependencies ++= Seq(
    "postgresql"          %   "postgresql"  %"9.1-901.jdbc4",
    "com.h2database"  %  "h2"                        % "1.4.177" % "test",
    "org.scalikejdbc" %% "scalikejdbc"               % scalikeV,
    "org.scalikejdbc" %% "scalikejdbc-config"        % scalikeV,
    "org.scalikejdbc" %% "scalikejdbc-interpolation" % scalikeV,
    "org.scalikejdbc" %% "scalikejdbc-test"          % scalikeV   % "test",
    //"com.typesafe.scala-logging" %% "scala-logging" % "2.1.2", //3.0.0 require scala 2.11
    "com.typesafe.scala-logging" % "scala-logging-slf4j_2.10" % "2.1.2",
    "ch.qos.logback"  %  "logback-classic"           % "1.1.2",
    "io.spray"            %   "spray-can"     % sprayV,
    "io.spray"            %   "spray-client"     % sprayV,
    "io.spray"            %   "spray-routing" % sprayV,
    "io.spray"            %   "spray-testkit" % sprayV,
    "io.spray"            %   "spray-http"   % sprayV,
    "io.spray"            %   "spray-httpx"   % sprayV,
    "io.spray"            %   "spray-util"   % sprayV,
    "org.json4s"          %%  "json4s-native" % json4sV, //"json4s-jackson"
    "org.json4s"          %%  "json4s-ext" % json4sV,
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV,
    "com.sksamuel.elastic4s" %% "elastic4s" % elastic4sV,
    "com.google.zxing" % "core" % "1.7",
    "org.specs2"          %%  "specs2"        % "2.2.3" % "test",
    "org.scala-lang.modules" %% "scala-async" % "0.9.0-M6" % "test"
)
//    "commons-codec" % "commons-codec" % "1.9",
//    "com.google.gdata" % "core" % "1.0",

seq(Revolver.settings: _*)

publishTo := {
  val nexus = "http://art.ebiznext.com/artifactory/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "libs-snapshot-local")
  else
    Some("releases"  at nexus + "libs-release-local")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishArtifact in (Compile, packageSrc) := false

publishArtifact in (Test, packageSrc) := false
