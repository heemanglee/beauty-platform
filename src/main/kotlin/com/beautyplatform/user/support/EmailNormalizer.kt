package com.beautyplatform.user.support

import java.util.Locale

object EmailNormalizer {
    fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)
}
