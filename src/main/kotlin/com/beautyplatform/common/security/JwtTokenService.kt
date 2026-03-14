package com.beautyplatform.common.security

import com.beautyplatform.user.User
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

data class IssuedToken(
    val accessToken: String,
    val expiresIn: Long,
)

@Service
class JwtTokenService(
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun issueToken(user: User): IssuedToken {
        val issuedAt = Instant.now(clock)
        val expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl)
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(jwtProperties.issuer)
                .subject(requireNotNull(user.id).toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", user.role.name)
                .claim("email", user.email)
                .claim("name", user.name)
                .build()
        val headers = JwsHeader.with(MacAlgorithm.HS256).build()

        val token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims))
        return IssuedToken(
            accessToken = token.tokenValue,
            expiresIn = jwtProperties.accessTokenTtl.seconds,
        )
    }
}
