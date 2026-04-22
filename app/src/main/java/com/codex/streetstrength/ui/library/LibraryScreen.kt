package com.codex.streetstrength.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.local.ExerciseCategoryWithTemplates
import com.codex.streetstrength.data.repository.CustomExerciseDraft
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.ui.formatSourceType
import com.codex.streetstrength.ui.rememberAppViewModel
import com.codex.streetstrength.ui.components.CustomExerciseDialog
import com.codex.streetstrength.ui.theme.StreetStrengthTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val catalog: List<ExerciseCategoryWithTemplates> = emptyList(),
)

class LibraryViewModel(
    private val repository: TrainingRepository,
) : ViewModel() {
    val uiState = repository.observeExerciseCatalog().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun createCustomExercise(draft: CustomExerciseDraft) {
        viewModelScope.launch {
            repository.createCustomExercise(draft)
        }
    }
}

@Composable
fun LibraryScreenRoute(app: StreetStrengthApp) {
    val viewModel = rememberAppViewModel { LibraryViewModel(app.trainingRepository) }
    val catalog by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = LibraryUiState(catalog = catalog),
        onCreateCustomExercise = viewModel::createCustomExercise,
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onCreateCustomExercise: (CustomExerciseDraft) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CustomExerciseDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { draft ->
                onCreateCustomExercise(draft)
                showCreateDialog = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "\u8bad\u7ec3\u5e93",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "\u5185\u7f6e\u9879\u76ee + \u81ea\u5b9a\u4e49\u9879\u76ee\uff0c\u65b0\u589e\u540e\u4f1a\u7acb\u5373\u51fa\u73b0\u5728\u8ba1\u5212\u9875\u3002",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.widthIn(min = 96.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("\u65b0\u589e\u9879\u76ee")
                    }
                }
            }
        }

        items(uiState.catalog, key = { it.category.id }) { category ->
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = category.category.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    category.templates.sortedBy { it.template.sortOrder }.forEach { template ->
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = template.template.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(
                                                if (template.template.supportsExternalLoad) {
                                                    "\u652f\u6301\u8d1f\u91cd"
                                                } else {
                                                    "\u81ea\u91cd"
                                                },
                                            )
                                        },
                                    )
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = { Text(formatSourceType(template.template.sourceType)) },
                                    )
                                }
                                template.template.cue?.takeIf { it.isNotBlank() }?.let { cue ->
                                    Text(cue, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(template.variants.sortedBy { it.sortOrder }, key = { it.id }) { variant ->
                                        AssistChip(
                                            onClick = {},
                                            enabled = false,
                                            label = { Text(variant.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryPreview() {
    StreetStrengthTheme {
        LibraryScreen(
            uiState = LibraryUiState(),
            onCreateCustomExercise = {},
        )
    }
}
