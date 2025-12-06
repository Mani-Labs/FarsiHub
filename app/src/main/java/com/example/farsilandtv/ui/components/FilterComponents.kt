package com.example.farsilandtv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared Filter Components - Extracted from MoviesScreen and ShowsScreen
 * Used for genre and sort filtering across content screens
 *
 * TV-L10 FIX: Added accessibility semantics for focus indicators and state
 */

/**
 * Genre Chip - Selectable filter chip for content filtering
 * TV-L10: Enhanced with accessibility semantics
 */
@Composable
fun GenreChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    // TV-L10: Accessibility state description
    val stateDesc = when {
        isSelected -> "Selected"
        isFocused -> "Focused"
        else -> "Not selected"
    }
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFFF59E0B)  // FarsilandAmber
            isSelected -> Color(0xFF2A2A35) // SurfaceLight
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "chip_bg"
    )
    val borderColor = when {
        isFocused -> Color(0xFFF59E0B)      // FarsilandAmber
        isSelected -> Color(0xFF2A2A35)     // SurfaceLight
        else -> Color(0x26FFFFFF)           // BorderHover (15% white)
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .semantics {
                role = Role.Button
                contentDescription = "$text genre filter"
                selected = isSelected
                stateDescription = stateDesc
            },
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isFocused || isSelected) Color.White else Color(0xFF71717A), // OnSurfaceVariant
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * Sort Button - Dropdown selector for sort options
 * TV-L10: Enhanced with accessibility semantics
 */
@Composable
fun SortButton(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .semantics {
                    role = Role.DropdownList
                    contentDescription = "Sort by $selected, tap to change"
                    stateDescription = if (expanded) "Menu expanded" else "Menu collapsed"
                },
            shape = RoundedCornerShape(8.dp),
            color = if (isFocused) Color(0xFFF59E0B) else Color(0xFF1A1A24) // Amber / SurfaceDark
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selected,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Open sort menu",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1A1A24)) // SurfaceDark
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == selected) Color(0xFFF59E0B) else Color.White // FarsilandAmber
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
