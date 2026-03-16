package me.nathanfallet.pokemonmaprandomizer.loader

import me.nathanfallet.pokemonmaprandomizer.model.Warp

class FlagAlias(
    val flag: String,
    val def: List<String>,
    val checks: List<Warp>,
    var equiv: MutableList<String> = mutableListOf(),
    var set: Boolean = false,
)
