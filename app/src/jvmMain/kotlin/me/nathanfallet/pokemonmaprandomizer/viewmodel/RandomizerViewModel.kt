package me.nathanfallet.pokemonmaprandomizer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.nathanfallet.pokemonmaprandomizer.Randomizer
import me.nathanfallet.pokemonmaprandomizer.model.Game
import me.nathanfallet.pokemonmaprandomizer.model.Season
import java.io.File

data class RandomizerUiState(
    val romPath: String = "",
    val game: Game? = null,
    val seedInput: String = "",
    val season: Season = Season.SPRING_SUMMER,
    val outputFolder: String = "",
    val status: RandomizerStatus = RandomizerStatus.Idle,
)

sealed class RandomizerStatus {
    object Idle : RandomizerStatus()
    object Running : RandomizerStatus()
    data class Success(val seedUsed: Long, val outputPath: String, val log: String) : RandomizerStatus()
    data class Error(val message: String) : RandomizerStatus()
}

class RandomizerViewModel : ViewModel() {

    private val _state = MutableStateFlow(RandomizerUiState())
    val state: StateFlow<RandomizerUiState> = _state.asStateFlow()

    fun selectRom(path: String) {
        val detected = detectGame(path)
        _state.update { it.copy(romPath = path, game = detected, status = RandomizerStatus.Idle) }
    }

    fun setGame(game: Game) {
        _state.update { it.copy(game = game) }
    }

    fun setSeed(seed: String) {
        _state.update { it.copy(seedInput = seed) }
    }

    fun setSeason(season: Season) {
        _state.update { it.copy(season = season) }
    }

    fun setOutputFolder(path: String) {
        _state.update { it.copy(outputFolder = path) }
    }

    fun randomize() {
        val s = _state.value
        val romPath = s.romPath
        val game = s.game
        val outputFolder = s.outputFolder

        if (romPath.isBlank()) {
            _state.update { it.copy(status = RandomizerStatus.Error("Please select a ROM file.")) }
            return
        }
        if (game == null) {
            _state.update { it.copy(status = RandomizerStatus.Error("Please select a game.")) }
            return
        }
        if (outputFolder.isBlank()) {
            _state.update { it.copy(status = RandomizerStatus.Error("Please select an output folder.")) }
            return
        }

        val seed: Long? = s.seedInput.trim().toLongOrNull()
        val season = if (game == Game.B2 || game == Game.W2) s.season else null

        _state.update { it.copy(status = RandomizerStatus.Running) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val romBytes = File(romPath).readBytes()
                val output = Randomizer().randomize(romBytes, game, seed, season)

                val romName = File(romPath).nameWithoutExtension
                val outFile = File(outputFolder, "${romName}_map_randomized.nds")
                outFile.writeBytes(output.rom)

                val logFile = File(outputFolder, "${romName}_map_randomized.log")
                logFile.writeText(output.log)

                _state.update {
                    it.copy(
                        status = RandomizerStatus.Success(
                            seedUsed = output.seedUsed,
                            outputPath = outFile.absolutePath,
                            log = output.log,
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(status = RandomizerStatus.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Game auto-detection from NDS ROM header (game code at 0x0C)
    // -------------------------------------------------------------------------

    private fun detectGame(romPath: String): Game? {
        return try {
            val header = File(romPath).inputStream().use { it.readNBytes(0x10) }
            if (header.size < 0x10) return null
            val code = header.decodeToString(0x0C, 0x0F) // first 3 chars of game code
            when (code) {
                "IPK" -> Game.HG
                "IPG" -> Game.SS
                "IRB" -> Game.B2
                "IRE" -> Game.W2
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
