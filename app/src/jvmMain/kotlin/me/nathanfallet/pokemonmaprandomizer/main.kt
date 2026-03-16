package me.nathanfallet.pokemonmaprandomizer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import me.nathanfallet.pokemonmaprandomizer.model.Game
import me.nathanfallet.pokemonmaprandomizer.model.Season
import java.io.File
import kotlin.system.exitProcess

/**
 * Entry point.
 *
 * CLI mode (all args required):
 *   <rom> <game> [seed] [season] <outputDir>
 *
 *   rom       — path to the .nds ROM
 *   game      — hg | ss | b2 | w2
 *   seed      — numeric seed, or "-" for random
 *   season    — spring | summer | autumn | winter  (only for b2/w2, omit otherwise)
 *   outputDir — directory to write the randomized ROM and log
 *
 * Examples:
 *   ./app hg.nds hg 12345 ./out
 *   ./app b2.nds b2 - autumn ./out
 *   ./app w2.nds w2 ./out          # random seed, default season
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty()) runCli(args)
    else runGui()
}

// ---- CLI -------------------------------------------------------------------

private fun runCli(args: Array<String>) {
    if (args.size < 3) {
        printUsage()
        exitProcess(1)
    }

    val romPath = args[0]
    val game = parseGame(args[1]) ?: run {
        System.err.println("Unknown game '${args[1]}'. Use: hg, ss, b2, w2")
        exitProcess(1)
    }
    val isBw2 = game == Game.B2 || game == Game.W2

    // Remaining args: [seed] [season] <outputDir>
    var idx = 2
    val seed: Long? = args[idx].let { if (it == "-") null else it.toLongOrNull() }
        .also { if (args[idx] != "-" && it != null || args[idx] == "-") idx++ else Unit }

    val season: Season? = if (isBw2 && idx < args.size - 1) {
        parseSeason(args[idx])?.also { idx++ }
    } else null

    if (idx >= args.size) {
        printUsage(); exitProcess(1)
    }
    val outputDir = args[idx]

    val romFile = File(romPath)
    if (!romFile.exists()) {
        System.err.println("ROM not found: $romPath"); exitProcess(1)
    }

    val outDir = File(outputDir).also { it.mkdirs() }

    println("Randomizing ${romFile.name} as ${game.name}…")
    if (seed != null) println("Seed: $seed") else println("Seed: (random)")
    if (season != null) println("Season: $season")

    val output = Randomizer().randomize(romFile.readBytes(), game, seed, season)

    val base = romFile.nameWithoutExtension
    val outRom = File(outDir, "${base}_map_randomized.nds")
    val outLog = File(outDir, "${base}_map_randomized.log")
    outRom.writeBytes(output.rom)
    outLog.writeText(output.log)

    println("Done!")
    println("Seed used : ${output.seedUsed}")
    println("ROM output: ${outRom.absolutePath}")
    println("Log output: ${outLog.absolutePath}")
}

private fun parseGame(s: String): Game? = when (s.lowercase()) {
    "hg", "heartgold" -> Game.HG
    "ss", "soulsilver" -> Game.SS
    "b2", "black2" -> Game.B2
    "w2", "white2" -> Game.W2
    else -> null
}

private fun parseSeason(s: String): Season? = when (s.lowercase()) {
    "spring", "summer", "springsummer", "spring_summer" -> Season.SPRING_SUMMER
    "autumn", "fall" -> Season.AUTUMN
    "winter" -> Season.WINTER
    else -> null
}

private fun printUsage() {
    println(
        """
Usage (CLI):  <rom> <game> [seed|-] [season] <outputDir>
  game:    hg | ss | b2 | w2
  seed:    numeric seed, or - for random
  season:  spring | autumn | winter  (BW2 only)

Examples:
  ./app game.nds hg 12345 ./out
  ./app game.nds b2 - autumn ./out
  ./app game.nds hg ./out

Usage (GUI):  (no arguments)
    """.trimIndent()
    )
}

// ---- GUI -------------------------------------------------------------------

private fun runGui() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pokémon Map Randomizer",
    ) {
        App()
    }
}
