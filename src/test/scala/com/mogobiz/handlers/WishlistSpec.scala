package com.mogobiz.handlers

import java.util.Calendar

import com.mogobiz.es.EsClient
import com.mogobiz.model._
import com.mogobiz.utils.GlobalUtil
import org.specs2.mutable.Specification

class WishlistSpec extends Specification {
  "Create WishlistList" in {
    val now = Calendar.getInstance().getTime
    val wll = WishlistList(GlobalUtil.newUUID,
      List(
        Wishlist(GlobalUtil.newUUID, "My Wishlist", WishlistVisibility.PRIVATE, false, GlobalUtil.newUUID,
          List(
            WishIdea(GlobalUtil.newUUID, "This is my idea"),
            WishIdea(GlobalUtil.newUUID, "This is my idea 2")
          ),
          List(
            WishItem(GlobalUtil.newUUID, "My 1st Item", "productuuid1"),
            WishItem(GlobalUtil.newUUID, "My 2nd Item", "productuuid2")
          ),
          List(WishBrand(GlobalUtil.newUUID, "brand1"), WishBrand(GlobalUtil.newUUID, "brand2")),
          List(WishCategory(GlobalUtil.newUUID, "Category 1"), WishCategory(GlobalUtil.newUUID, "Category 2")),
          false,
          now,
          now
        )
      ),
      WishlistOwner("email@email.com", Some("Me"), Some(10), Some(8), Some("description")), now, now
    )
    EsClient.index(WishlistHandler.esStore("mogobiz"), wll) must not beNull
  }
}
