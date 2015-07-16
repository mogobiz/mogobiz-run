/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

// Statut : requete fausse, manque de donnnées valorisé (endPeriods) ==> revoir le jeu de données
// StoreController
restpath = "/api/store/{storeCode}/product/productId}/dates?start={startDate}&end={endDate}";
method="GET";

/* TODO dans StoreController
 calcul des montants
 filtrer les traductions

 */

//Requete ES
method = "POST";
curl = "http://localhost:9200/mogobiz/product/_search?q=_id:106"; //Id du produit à passer en parametre

query = {
    "_source": {
        "includes": [
            "intraDayPeriods",
            "datePeriods"
        ]
    }
};

