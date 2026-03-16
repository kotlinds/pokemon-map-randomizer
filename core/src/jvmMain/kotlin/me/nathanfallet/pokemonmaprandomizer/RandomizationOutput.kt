package me.nathanfallet.pokemonmaprandomizer

data class RandomizationOutput(
    val rom: ByteArray,
    val log: String,
    val seedUsed: Long,
)
