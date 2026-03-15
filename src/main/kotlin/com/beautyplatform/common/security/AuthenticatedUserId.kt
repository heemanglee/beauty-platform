package com.beautyplatform.common.security

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

fun JwtAuthenticationToken.requireUserId(): Long =
    name.toLongOrNull() ?: throw IllegalStateException("JWT subject must be a numeric user id")
