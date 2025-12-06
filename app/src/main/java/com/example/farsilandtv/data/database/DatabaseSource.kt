package com.example.farsilandtv.data.database

/**
 * Database source options for content catalog
 */
enum class DatabaseSource(
    val displayName: String,
    val fileName: String,
    val urlPattern: String  // URL pattern for filtering content by source
) {
    FARSILAND("Farsiland.com", "farsiland_content.db", "%farsiland.com%"),
    FARSIPLEX("FarsiPlex.com", "farsiplex_content.db", "%farsiplex.com%"),
    NAMAKADE("Namakade.com", "namakade.db", "%namakade%"),
    IMVBOX("IMVBox.com", "imvbox_content.db", "%imvbox%");

    companion object {
        fun fromFileName(fileName: String): DatabaseSource {
            return values().find { it.fileName == fileName } ?: FARSILAND
        }
    }
}
