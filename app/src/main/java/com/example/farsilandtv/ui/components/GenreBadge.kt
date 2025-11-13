package com.example.farsilandtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.farsilandtv.ui.theme.*

/**
 * Feature #16: Compose Component - Genre Badge
 * Displays a genre tag with color coding (used in cards and detail screens)
 */
@Composable
fun GenreBadge(
    genreName: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = getGenreColor(genreName)

    Surface(
        color = backgroundColor.copy(alpha = 0.8f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = genreName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/**
 * Maps genre names to their color codes
 * Matches existing color scheme from Feature #3
 */
private fun getGenreColor(genreName: String): Color {
    return when (genreName.lowercase()) {
        "اکشن", "action" -> GenreAction
        "کمدی", "comedy" -> GenreComedy
        "درام", "drama" -> GenreDrama
        "ترسناک", "horror" -> GenreHorror
        "رمانتیک", "romance" -> GenreRomance
        "علمی-تخیلی", "sci-fi", "science fiction" -> GenreSci_Fi
        "تریلر", "thriller" -> GenreThriller
        "انیمیشن", "animation" -> GenreAnimation
        "مستند", "documentary" -> GenreDocumentary
        "خانوادگی", "family" -> GenreFamily
        "فانتزی", "fantasy" -> GenreFantasy
        "جنایی", "crime" -> GenreCrime
        "معمایی", "mystery" -> GenreMystery
        "ماجراجویی", "adventure" -> GenreAdventure
        "جنگی", "war" -> GenreWar
        "تاریخی", "history" -> GenreHistory
        "موزیکال", "music" -> GenreMusic
        "وسترن", "western" -> GenreWestern
        "بیوگرافی", "biography" -> GenreBiography
        else -> Color(0xFF607D8B) // Default gray
    }
}
