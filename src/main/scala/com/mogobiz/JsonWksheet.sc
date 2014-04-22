import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._

val tva="TVA"
val product = (
    ("id" ->1 ) ~
      ("price" -> 1000000) ~
      ("taxRate"-> (
        ("id"->1)~("name"->tva)~("localTaxRates"-> (List(
          ("id"->10)~("rate"->19.6)~("country"->"FR")~("stateCode"->null),
          ("id"->11)~("rate"->17)~("country"->"EN")~("stateCode"->null),
          ("id"->12)~("rate"->(None:Option[Double]))~("country"->"ES")~("stateCode"->null)
        )))
        ))

    )
val formated = compact(render(product))
val p = parse(formated)
val price = (product \ "price") //.extract[Int]

val taxRate = product \ "taxRate"
val locals = (taxRate \ "localTaxRates")
locals.children.children
for{
  JObject(rate) <- (taxRate \ "localTaxRates").children.children
  JField("country",JString(countryCode)) <- rate
  JField("rate",rateValue) <- rate
  if(countryCode=="FR")
} yield (countryCode,rateValue)


val lrt = for {
  localTaxRate @ JObject(x) <- taxRate \ "localTaxRates"
  if (x contains JField("country", JString("FR")))
  JField("rate",value) <- x } yield value

val rate = lrt.headOption match {
  case Some(JDouble(x)) => x
  case Some(JInt(x)) => x.toDouble
  case Some(JNothing) => 0
  case _ => 0
}
//ips.headOption
//rate <- localTaxRate \ "rate"