package me.nathanfallet.pokemonmaprandomizer.randomizer

import me.nathanfallet.pokemonmaprandomizer.loader.FlagAlias
import me.nathanfallet.pokemonmaprandomizer.loader.ProgressionStep

// -------------------------------------------------------------------------
// Utilities
// -------------------------------------------------------------------------

fun <T> MutableList<T>.removeIfPresent(element: T) {
    val idx = indexOf(element)
    if (idx != -1) {
        swap(idx, size - 1); removeLast()
    }
}

fun <T> MutableList<T>.swap(i: Int, j: Int) {
    val tmp = this[i]; this[i] = this[j]; this[j] = tmp
}

/** Thin wrapper so we can call `List.shuffle(random)` with a seeded [kotlin.random.Random]. */
class KotlinRandom(seed: Long) {
    val random = kotlin.random.Random(seed)
}

fun FlagAlias.copy() = FlagAlias(flag, def, checks, equiv.toMutableList(), set)
fun ProgressionStep.copy() = ProgressionStep(warp, flags)
