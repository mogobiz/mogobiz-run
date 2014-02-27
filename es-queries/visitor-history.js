// StoreController
restpath = "/mogobiz/store/{storeCode}/history/{visitorId}?lang={lang}&currency={curCode}&country={countryCode}"
method="GET"

/* TODO dans StoreController
calcul des montants
filtrer les traductions

Remarque doc YBA : quel cookie ??
 */

//Requete ES
method = "PUT"
curl = "http://localhost:9200/mogobiz/history"
query = {

}
/* TODO ES : renvoyer la liste des produits via les enfants


 */
