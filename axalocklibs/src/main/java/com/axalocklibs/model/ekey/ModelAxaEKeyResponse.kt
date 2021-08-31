package com.kolonishare.booking.model.ekey

import com.google.gson.annotations.SerializedName

class ModelAxaEKeyResponse {

    @SerializedName("MESSAGE")
    var mESSAGE: String? = ""

    @SerializedName("FLAG")
    var isFLAG = false

    @SerializedName("IS_ACTIVE")
    var isISACTIVE = false

    @SerializedName("ASSETS_DETAIL")
    var aSSETSDETAIL: ASSETSDETAIL? = null
}