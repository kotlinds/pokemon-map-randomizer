package me.nathanfallet.pokemonmaprandomizer.model

/** A group of warps forming a single location. */
typealias Block = MutableList<Warp>

fun mutableBlockOf(vararg warps: Warp): Block = mutableListOf(*warps)
