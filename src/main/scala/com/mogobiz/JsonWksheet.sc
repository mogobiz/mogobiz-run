import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._

val product = (
    ("id" ->1 ) ~
      ("price" -> 1000000) ~
      ("taxRate"-> (
        ("id"->1)~("name"->"TVA")~("localTaxRates"-> (List(
          ("id"->10)~("rate"->19.6)~("countryCode"->"FR")~("stateCode"->null),
          ("id"->11)~("rate"->17)~("countryCode"->"EN")~("stateCode"->null),
          ("id"->12)~("rate"->(None:Option[Double]))~("countryCode"->"ES")~("stateCode"->null)
        )))
        ))

    )
val formated = compact(render(product))
val p = parse(formated)
val price = (product \ "price") //.extract[Int]

val taxRate = product \ "taxRate"
(taxRate \ "localTaxRates")
for{
  JObject(rate) <- (taxRate \ "localTaxRates")
  JField("countryCode",JString(countryCode)) <- rate
  JField("rate",JDouble(rateValue)) <- rate
} yield rateValue

