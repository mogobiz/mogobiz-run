//import spray.revolver.RevolverPlugin.Revolver
//
//import AssemblyKeys._
//
//assemblySettings
//
//jarName in assembly := s"${name.value}-${version.value}.jar"
//
//mainClass in assembly := Some("com.mogobiz.Rest")
//
//test in assembly := {}
//
//assemblyMainClass := Some("com.mogobiz.Rest")
//
//scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

//resolvers += "Grep code" at "http://grepcode.com/snapshot/repo1.maven.org/maven2/"

libraryDependencies in ThisBuild ++= Seq(
  "org.apache.derby" % "derby" % "10.11.1.1" % "test"
)
//packAutoSettings

name:= "mogobiz-run"


scalariformSettings