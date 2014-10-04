package mogobiz

import sbt._
import sbt.Keys._
import java.io.File

import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin._
import spray.revolver.RevolverPlugin.Revolver

/**
 * @author stephane.manciot@ebiznext.com
 *
 */
object DbProfilePlugin extends Plugin {

  def entries(f: File):List[File] = f :: (if (f.isDirectory) IO.listFiles(f).toList.flatMap(entries) else Nil)

  trait Keys {
    def Config:Configuration
    lazy val profile2jar = taskKey[File]("Generates profile jar file")
    lazy val defaultSettings = assemblySettings ++ Revolver.settings ++ Seq(
      jarName in assembly := s"${name.value}-${Config.name}-${version.value}.jar",
      mainClass in assembly := Some("com.mogobiz.Rest"),
      test in assembly := {},
      profile2jar := {
        val destDir = baseDirectory.value
        val jarFile = Config.name + ".jar"
        val confDir = baseDirectory.value / "src" / Config.name / "resources"
        IO.zip(entries(confDir).map(d => (d, d.getAbsolutePath.substring(confDir.getAbsolutePath.length))), destDir / jarFile)
        destDir / jarFile
      },
      fullClasspath in assembly <+= profile2jar
    )
  }

  object PostgreSQL extends Keys{
    lazy val Config = config("postgresql") extend Compile
    lazy val settings = Seq(ivyConfigurations += Config) ++ inConfig(Config)(
      defaultSettings ++ Seq(
        mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
          {
            case PathList("com", "ibm", "icu", xs @ _*) => MergeStrategy.discard
            case PathList("com", "mysql", xs @ _*) => MergeStrategy.discard
            case PathList("oracle", xs @ _*) => MergeStrategy.discard
            case "application.conf" => MergeStrategy.concat
            case x => old(x)
          }
        }
      )
    )
  }

  object MySQL extends Keys{
    lazy val Config = config("mysql") extend Compile
    lazy val settings = Seq(ivyConfigurations += Config) ++ inConfig(Config)(
      defaultSettings ++ Seq(
        mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
          {
            case PathList("com", "ibm", "icu", xs @ _*) => MergeStrategy.discard
            case PathList("oracle", xs @ _*) => MergeStrategy.discard
            case PathList("org", "postgresql", xs @ _*) => MergeStrategy.discard
            case "application.conf" => MergeStrategy.concat
            case x => old(x)
          }
        }
      )
    )
  }

  object Oracle extends Keys{
    lazy val Config = config("oracle") extend Compile
    lazy val settings = Seq(ivyConfigurations += Config) ++ inConfig(Config)(
      defaultSettings ++ Seq(
        mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
          {
            case PathList("com", "ibm", "icu", xs @ _*) => MergeStrategy.discard
            case PathList("com", "mysql", xs @ _*) => MergeStrategy.discard
            case PathList("org", "postgresql", xs @ _*) => MergeStrategy.discard
            case "application.conf" => MergeStrategy.concat
            case x => old(x)
          }
        }
      )
    )
  }

}