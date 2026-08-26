package com.avsp.pro.dataset.model

/**
 * M7 dataset categories. Architecture allows expansion without schema change
 * (stored as free-form String in DB; this enum is the known set).
 */
enum class DatasetCategory(val key: String, val displayName: String) {
    INTRO("Intro", "Intro"),
    OUTRO("Outro", "Outro"),
    STATION("Station", "Station"),
    ROAD("Road", "Road"),
    MARKET("Market", "Market"),
    TEMPLE("Temple", "Temple"),
    NATURE("Nature", "Nature"),
    PEOPLE("People", "People"),
    VEHICLE("Vehicle", "Vehicle"),
    FOOD("Food", "Food"),
    BUILDINGS("Buildings", "Buildings"),
    UNCATEGORIZED("Uncategorized", "Uncategorized");

    companion object {
        fun fromKey(key: String?): DatasetCategory {
            if (key.isNullOrBlank()) return UNCATEGORIZED
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: UNCATEGORIZED
        }

        fun allKnown(): List<DatasetCategory> = entries.filter { it != UNCATEGORIZED }
    }
}
