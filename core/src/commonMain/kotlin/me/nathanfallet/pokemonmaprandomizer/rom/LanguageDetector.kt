package me.nathanfallet.pokemonmaprandomizer.rom

import me.nathanfallet.pokemonmaprandomizer.model.Game
import me.nathanfallet.pokemonmaprandomizer.model.Language

/**
 * Detects the language of a ROM from its decompressed ARM9 binary.
 *
 * HGSS: read byte at offset 0xF5670.
 *   2 = English, 3 = French, 5 = German, 164 = Spanish
 *
 * BW2: read byte at offset 0x0E.
 *   62 (W2) / 35 (B2) = English, 227 (W2) / 171 (B2) = German
 */
object LanguageDetector {

    fun detect(arm9: ByteArray, game: Game): Language {
        return when (game) {
            Game.HG, Game.SS -> detectHgss(arm9)
            Game.B2, Game.W2 -> detectBw2(arm9)
        }
    }

    private fun detectHgss(arm9: ByteArray): Language {
        if (arm9.size <= 0xF5670) return Language.ENGLISH
        return when (arm9[0xF5670].toInt() and 0xFF) {
            3 -> Language.FRENCH
            5 -> Language.GERMAN
            164 -> Language.SPANISH
            else -> Language.ENGLISH
        }
    }

    private fun detectBw2(arm9: ByteArray): Language {
        if (arm9.size <= 0x0E) return Language.ENGLISH
        return when (arm9[0x0E].toInt() and 0xFF) {
            227, 171 -> Language.GERMAN
            else -> Language.ENGLISH
        }
    }
}
