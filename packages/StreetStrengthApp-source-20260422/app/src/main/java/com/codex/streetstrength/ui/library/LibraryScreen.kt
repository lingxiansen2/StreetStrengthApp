package com.codex.streetstrength.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.local.ExerciseCategoryEntity
import com.codex.streetstrength.data.local.ExerciseCategoryWithTemplates
import com.codex.streetstrength.data.local.ExerciseTemplateEntity
import com.codex.streetstrength.data.local.ExerciseTemplateWithVariants
import com.codex.streetstrength.data.local.ExerciseVariantEntity
import com.codex.streetstrength.data.preferences.PreferencesRepository
import com.codex.streetstrength.data.repository.CustomExerciseDraft
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.ui.components.CustomExerciseDialog
import com.codex.streetstrength.ui.formatMetricType
import com.codex.streetstrength.ui.formatSourceType
import com.codex.streetstrength.ui.rememberAppViewModel
import com.codex.streetstrength.ui.theme.StreetStrengthTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val ALL_FILTER = "all"
private const val FAVORITES_FILTER = "favorites"

data class LibraryUiState(
    val catalog: List<ExerciseCategoryWithTemplates> = emptyList(),
    val favoriteTemplateIds: Set<Long> = emptySet(),
)

private data class LibraryTemplateItem(
    val category: ExerciseCategoryEntity,
    val template: ExerciseTemplateEntity,
    val variants: List<ExerciseVariantEntity>,
)

class LibraryViewModel(
    private val repository: TrainingRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val uiState = combine(
        repository.observeExerciseCatalog(),
        preferencesRepository.preferencesFlow,
    ) { catalog, prefs ->
        LibraryUiState(
            catalog = catalog,
            favoriteTemplateIds = prefs.favoriteTemplateIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun createCustomExercise(draft: CustomExerciseDraft) {
        viewModelScope.launch {
            repository.createCustomExercise(draft)
        }
    }

    fun toggleFavoriteTemplate(templateId: Long) {
        viewModelScope.launch {
            preferencesRepository.toggleFavoriteTemplate(templateId)
        }
    }
}

@Composable
fun LibraryScreenRoute(app: StreetStrengthApp) {
    val viewModel = rememberAppViewModel {
        LibraryViewModel(
            repository = app.trainingRepository,
            preferencesRepository = app.preferencesRepository,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        onCreateCustomExercise = viewModel::createCustomExercise,
        onToggleFavorite = viewModel::toggleFavoriteTemplate,
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onCreateCustomExercise: (CustomExerciseDraft) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ALL_FILTER) }
    val uriHandler = LocalUriHandler.current
    val allTemplates = remember(uiState.catalog) {
        uiState.catalog.flatMap { category ->
            category.templates
                .sortedBy { it.template.sortOrder }
                .map { template ->
                    LibraryTemplateItem(
                        category = category.category,
                        template = template.template,
                        variants = template.variants.sortedBy { it.sortOrder },
                    )
                }
        }
    }
    val favoriteCount = remember(allTemplates, uiState.favoriteTemplateIds) {
        allTemplates.count { it.template.id in uiState.favoriteTemplateIds }
    }

    val filteredTemplates = remember(
        allTemplates,
        query,
        selectedFilter,
        uiState.favoriteTemplateIds,
    ) {
        allTemplates
            .filter { item ->
                when (selectedFilter) {
                    FAVORITES_FILTER -> item.template.id in uiState.favoriteTemplateIds
                    ALL_FILTER -> true
                    else -> selectedFilter == categoryFilterKey(item.category.id)
                }
            }
            .filter { item -> item.matches(query) }
    }

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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LibraryHeaderCard(
                totalCount = allTemplates.size,
                favoriteCount = favoriteCount,
                onCreateClick = { showCreateDialog = true },
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                label = { Text("搜索动作 / 变式 / 要点") },
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedFilter == ALL_FILTER,
                        onClick = { selectedFilter = ALL_FILTER },
                        label = { Text("全部") },
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == FAVORITES_FILTER,
                        onClick = { selectedFilter = FAVORITES_FILTER },
                        label = { Text("常用") },
                    )
                }
                items(uiState.catalog, key = { it.category.id }) { category ->
                    val key = categoryFilterKey(category.category.id)
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(category.category.name) },
                    )
                }
            }
        }

        item {
            Text(
                text = "当前显示 ${filteredTemplates.size} / ${allTemplates.size} 个动作",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (filteredTemplates.isEmpty()) {
            item {
                EmptyLibraryResult(
                    isFavorites = selectedFilter == FAVORITES_FILTER,
                    hasQuery = query.isNotBlank(),
                )
            }
        } else {
            items(filteredTemplates, key = { it.template.id }) { item ->
                LibraryTemplateCard(
                    item = item,
                    isFavorite = item.template.id in uiState.favoriteTemplateIds,
                    onToggleFavorite = { onToggleFavorite(item.template.id) },
                    onOpenTutorial = {
                        buildLibraryTutorialUrl(
                            templateName = item.template.name,
                            variantNames = item.variants.map { it.name },
                        )?.let { url ->
                            runCatching { uriHandler.openUri(url) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryHeaderCard(
    totalCount: Int,
    favoriteCount: Int,
    onCreateClick: () -> Unit,
) {
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
                    text = "训练库",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "按分类、搜索和常用动作快速定位；共 $totalCount 个动作，已收藏 $favoriteCount 个。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onCreateClick,
                modifier = Modifier.widthIn(min = 96.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("新增项目")
            }
        }
    }
}

@Composable
private fun LibraryTemplateCard(
    item: LibraryTemplateItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenTutorial: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.template.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${item.category.name} · ${item.variants.size} 个变式",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (isFavorite) "取消常用" else "加入常用",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                if (item.template.supportsExternalLoad) {
                                    "支持负重"
                                } else {
                                    "自重"
                                },
                            )
                        },
                    )
                }
                item {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(formatMetricType(item.template.defaultMetricType)) },
                    )
                }
                item {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(formatSourceType(item.template.sourceType)) },
                    )
                }
                item {
                    AssistChip(
                        onClick = onOpenTutorial,
                        leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                        label = { Text("教程") },
                    )
                }
            }

            item.template.cue?.takeIf { it.isNotBlank() }?.let { cue ->
                Text(
                    text = cue,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(item.variants, key = { it.id }) { variant ->
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

@Composable
private fun EmptyLibraryResult(
    isFavorites: Boolean,
    hasQuery: Boolean,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    isFavorites -> "还没有常用动作"
                    hasQuery -> "没有匹配的动作"
                    else -> "这个分类暂无动作"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    isFavorites -> "点击动作右侧星标后，会出现在常用筛选中。"
                    hasQuery -> "换一个关键词，或者清空搜索后再查看。"
                    else -> "可以通过新增项目创建自定义动作。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun categoryFilterKey(categoryId: Long): String = "category:$categoryId"

private fun LibraryTemplateItem.matches(rawQuery: String): Boolean {
    val query = rawQuery.trim()
    if (query.isBlank()) return true
    return template.name.contains(query, ignoreCase = true) ||
        category.name.contains(query, ignoreCase = true) ||
        formatSourceType(template.sourceType).contains(query, ignoreCase = true) ||
        formatMetricType(template.defaultMetricType).contains(query, ignoreCase = true) ||
        template.cue.orEmpty().contains(query, ignoreCase = true) ||
        variants.any { variant ->
            variant.name.contains(query, ignoreCase = true) ||
                variant.cue.orEmpty().contains(query, ignoreCase = true)
        }
}

private fun buildLibraryTutorialUrl(
    templateName: String,
    variantNames: List<String>,
): String? {
    val primaryVariant = variantNames.firstOrNull()?.takeIf { it.isNotBlank() }
    val keyword = listOfNotNull(templateName.trim(), primaryVariant?.trim())
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (keyword.isBlank()) return null
    val query = "$keyword 街健 动作教程"
    val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
    return "https://search.bilibili.com/all?keyword=$encoded"
}

@Preview(showBackground = true)
@Composable
private fun LibraryPreview() {
    StreetStrengthTheme {
        LibraryScreen(
            uiState = LibraryUiState(),
            onCreateCustomExercise = {},
            onToggleFavorite = {},
        )
    }
}
