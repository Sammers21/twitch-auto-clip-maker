package io.github.sammers21.tacm.server

data class TwitchToken(
        val access_token: String,
        val expires_in: Int,
        val refresh_token: String,
        val scope: List<String>,
        val token_type: String
)