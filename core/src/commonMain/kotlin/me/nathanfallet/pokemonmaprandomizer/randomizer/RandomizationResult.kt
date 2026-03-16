package me.nathanfallet.pokemonmaprandomizer.randomizer

import me.nathanfallet.pokemonmaprandomizer.model.Warp

// -------------------------------------------------------------------------
// Result
// -------------------------------------------------------------------------

data class RandomizationResult(
    /** All warps with [Warp.newWarp] filled in. */
    val warps: List<Warp>,
    val log: String,
)
