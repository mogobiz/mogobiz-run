/**
 * Created by Christophe on 16/03/14.
 */
object Langs {


  def listAllLanguages() : List[String] = {
    return "fr"::"en"::"es"::"de"::Nil
  }
  def getAllExcludedLanguagesExcept(langRequested:String) : List[String] = {
    return listAllLanguages().filter{
      lang => lang!=langRequested
    }
  }
  getAllExcludedLanguagesExcept("fr").map{ l => "*"+l}.mkString("\"","\",\"","\"")

  getAllExcludedLanguagesExcept("fr").flatMap{ l => "*"+l::"*."+l::Nil}.mkString("\"","\",\"","\"")

}
