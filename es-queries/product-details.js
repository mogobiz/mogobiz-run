/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

// Statut : OK
// StoreController
restpath = "/api/store/{storeCode}/products/{productId}?lang=fr&historize=true&visitorId={visitorId}&currency={curCode}&country={countryCode}";

/* TODO dans StoreController
si historize==true alors la méthode déclenche l'appel à la méthode addToHistory avec visitorId en paramètre
calcul des prix
virer les traductions en trop
 */

//Requete ES
method = "GET";
curl = "http://localhost:9200/{storeCode}/product/{productId}";
ex = "http://localhost:9200/mogobiz/product/57";

