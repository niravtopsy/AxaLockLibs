package com.kolonishare.booking.model.ekey

import com.google.gson.annotations.SerializedName

class ASSETSDETAIL {

    @SerializedName("axa_ekey")
    var axaEkey: String? = ""

    @SerializedName("sequence")
    var sequence = 0

    @SerializedName("axa_passkey_type")
    var axaPasskeyType: String? = ""

    @SerializedName("axa_passkey")
    var axaPasskey: String? = ""

    @SerializedName("slot_position")
    var slotPosition = 0
}