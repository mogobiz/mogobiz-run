// StoreController
restpath = "/mogobiz/store/{storeCode}/history/{visitorId}" //currency={curCode}&country={countryCode} ???
method="PUT"
payload={
    "products":"{productId}"
    "visitorId":
}
/* TODO dans StoreController


 */

//Requete ES
method = "PUT"
curl = "http://localhost:9200/mogobiz/history"
query = {

}
/* TODO ES : upsert d'un document au format
 payload = {
 id:"{visitorId}",
 products:["{productId}"]
 }
 */
