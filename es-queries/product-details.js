// StoreController
restpath = "/mogobiz/store/{storeCode}/products/{productId}/details?lang=fr&historize=true&visitorId={visitorId}&currency={curCode}&country={countryCode}"

/* TODO dans StoreController
si historize==true alors la méthode déclenche l'appel à la méthode addToHistory avec visitorId en paramètre
calcul des prix
virer les traductions en trop (voir remarque ES en-dessous)
 */

//Requete ES
method = "GET"
curl = "http://localhost:9200/mogobiz/product/57"
query = {

}

/*
 REMARQUE : IMPOSSIBLE de filter les traductions avec le paramètre _source/exclude avec un GET
 le document étant énorme, faire la suppression des langs dans le StoreController

 */

/*
 {
 "fields": [
 "website.en"
 ],
 "_source": {
 "excludes": [
 "*.*"
 ]
 }
 }
 */