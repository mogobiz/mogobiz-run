import spray.revolver.RevolverPlugin.Revolver

organization  := "com.mogobiz"

version       := "0.1"

scalaVersion  := "2.10.3"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

libraryDependencies ++= {
  val akkaV = "2.2.3"
  val sprayV = "1.2.0"
  val json4sV = "3.2.6"
  Seq(
    "io.spray"            %   "spray-can"     % sprayV,
    "io.spray"            %   "spray-client"     % sprayV,
    "io.spray"            %   "spray-routing" % sprayV,
    "io.spray"            %   "spray-testkit" % sprayV,
    "io.spray"            %   "spray-http"   % sprayV,
    "io.spray"            %   "spray-httpx"   % sprayV,
    "io.spray"            %   "spray-util"   % sprayV,
    "org.json4s"          %%  "json4s-native" % json4sV, //"json4s-jackson"
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV,
    "org.specs2"          %%  "specs2"        % "2.2.3" % "test",
    "org.scala-lang.modules" %% "scala-async" % "0.9.0-M6" % "test"
  )
}

seq(Revolver.settings: _*)
