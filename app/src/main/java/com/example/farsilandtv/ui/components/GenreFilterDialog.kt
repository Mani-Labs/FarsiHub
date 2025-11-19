package com.example.farsilandtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Genre Filter Dialog - TV-optimized with D-pad navigation
 *
 * Features:
 * - Multi-select checkboxes in grid layout
 * - TV-friendly focus management
 * - Badge showing active filter count
 * - Clear all and Apply buttons
 * - D-pad navigation support
 *
 * @param availableGenres List of all available genres
 * @param selectedGenres Set of currently selected genres
 * @param onGenreToggle Callback when a genre is toggled
 * @param onDismiss Callback to close dialog without applying
 * @param onApply Callback to apply filters and close dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreFilterDialog(
    availableGenres: List<String>,
    selectedGenres: Set<String>,
    onGenreToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                // Header with title and selected count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter by Genre",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (selectedGenres.isNotEmpty()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(text = "${selectedGenres.size} selected")
                        }
                    }
                }

                Divider(modifier = Modifier.padding(bottom = 16.dp))

                // Genre grid with checkboxes (TV-optimized with focus)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(availableGenres) { genre ->
                        GenreCheckboxItem(
                            genre = genre,
                            isSelected = genre in selectedGenres,
                            onToggle = { onGenreToggle(genre) }
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
                ) {
                    // Clear All button (only show if filters are selected)
                    if (selectedGenres.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                availableGenres.forEach { genre ->
                                    if (genre in selectedGenres) {
                                        onGenreToggle(genre) // Deselect all
                                    }
                                }
                            },
                            modifier = Modifier.width(150.dp)
                        ) {
                            Text("Clear All")
                        }
                    }

                    // Cancel button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(150.dp)
                    ) {
                        Text("Cancel")
                    }

                    // Apply button
                    Button(
                        onClick = {
                            onApply()
                            onDismiss()
                        },
                        modifier = Modifier.width(150.dp)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

/**
 * Genre Checkbox Item - TV-optimized with focus indication
 *
 * @param genre Genre name
 * @param isSelected Whether the genre is selected
 * @param onToggle Callback when checkbox is toggled
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreCheckboxItem(
    genre: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        shape = RoundedCornerShape(8.dp),
        border = if (isFocused) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
