@file:OptIn(ExperimentalMaterial3Api::class)

package me.nathanfallet.pokemonmaprandomizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.nathanfallet.pokemonmaprandomizer.model.Game
import me.nathanfallet.pokemonmaprandomizer.model.Season
import me.nathanfallet.pokemonmaprandomizer.viewmodel.RandomizerStatus
import me.nathanfallet.pokemonmaprandomizer.viewmodel.RandomizerViewModel
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun App(vm: RandomizerViewModel = viewModel { RandomizerViewModel() }) {
    val state by vm.state.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Pokémon Map Randomizer", style = MaterialTheme.typography.headlineMedium)

                HorizontalDivider()

                // ---- ROM file picker ----------------------------------------
                LabeledRow("ROM file") {
                    OutlinedTextField(
                        value = state.romPath,
                        onValueChange = { vm.selectRom(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Select a .nds ROM file…") },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { pickFile("nds")?.let { vm.selectRom(it) } }) {
                        Text("Browse")
                    }
                }

                // ---- Game selector ------------------------------------------
                LabeledRow("Game") {
                    GameDropdown(
                        selected = state.game,
                        onSelect = { vm.setGame(it) },
                        modifier = Modifier.width(220.dp),
                    )
                }

                // ---- Seed ---------------------------------------------------
                LabeledRow("Seed") {
                    OutlinedTextField(
                        value = state.seedInput,
                        onValueChange = { vm.setSeed(it) },
                        modifier = Modifier.width(220.dp),
                        placeholder = { Text("Leave blank for random") },
                        singleLine = true,
                    )
                }

                // ---- Season (BW2 only) --------------------------------------
                val isBw2 = state.game == Game.B2 || state.game == Game.W2
                if (isBw2) {
                    LabeledRow("Season") {
                        SeasonDropdown(
                            selected = state.season,
                            onSelect = { vm.setSeason(it) },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }

                // ---- Output folder ------------------------------------------
                LabeledRow("Output folder") {
                    OutlinedTextField(
                        value = state.outputFolder,
                        onValueChange = { vm.setOutputFolder(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Select output folder…") },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { pickFolder()?.let { vm.setOutputFolder(it) } }) {
                        Text("Browse")
                    }
                }

                HorizontalDivider()

                // ---- Randomize button ---------------------------------------
                Button(
                    onClick = { vm.randomize() },
                    enabled = state.status !is RandomizerStatus.Running,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Randomize")
                }

                // ---- Status area --------------------------------------------
                when (val status = state.status) {
                    is RandomizerStatus.Idle -> {}

                    is RandomizerStatus.Running -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Randomizing…")
                        }
                    }

                    is RandomizerStatus.Success -> {
                        SuccessPanel(status)
                    }

                    is RandomizerStatus.Error -> {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                text = "Error: ${status.message}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Success panel ----------------------------------------------------------

@Composable
private fun SuccessPanel(status: RandomizerStatus.Success) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Done!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text("Seed used: ${status.seedUsed}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Output: ${status.outputPath}", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Text("Log", style = MaterialTheme.typography.titleSmall)
        SelectionContainer {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = status.log,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

// ---- Labeled row helper -----------------------------------------------------

@Composable
private fun LabeledRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

// ---- Dropdowns --------------------------------------------------------------

@Composable
private fun GameDropdown(selected: Game?, onSelect: (Game) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.displayName ?: "Auto-detect",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Game.entries.forEach { game ->
                DropdownMenuItem(
                    text = { Text(game.displayName) },
                    onClick = { onSelect(game); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SeasonDropdown(selected: Season, onSelect: (Season) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Season.entries.forEach { season ->
                DropdownMenuItem(
                    text = { Text(season.displayName) },
                    onClick = { onSelect(season); expanded = false },
                )
            }
        }
    }
}

// ---- Display names ----------------------------------------------------------

private val Game.displayName
    get() = when (this) {
        Game.HG -> "HeartGold"
        Game.SS -> "SoulSilver"
        Game.B2 -> "Black 2"
        Game.W2 -> "White 2"
    }

private val Season.displayName
    get() = when (this) {
        Season.SPRING_SUMMER -> "Spring / Summer"
        Season.AUTUMN -> "Autumn"
        Season.WINTER -> "Winter"
    }

// ---- File / folder pickers (AWT) --------------------------------------------

private fun pickFile(vararg extensions: String): String? {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    val chooser = JFileChooser()
    if (extensions.isNotEmpty()) {
        chooser.fileFilter = FileNameExtensionFilter(
            extensions.joinToString(", ") { it.uppercase() } + " files",
            *extensions,
        )
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
        chooser.selectedFile.absolutePath
    else null
}

private fun pickFolder(): String? {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
        chooser.selectedFile.absolutePath
    else null
}
