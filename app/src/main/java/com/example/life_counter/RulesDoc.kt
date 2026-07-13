package com.example.life_counter

/**
 * The official Flesh and Blood rules documents, all served as plain text from
 * rules.fabtcg.com and all sharing the same numbered structure. The
 * Comprehensive Rules is the default; the other two are available to switch to.
 */
enum class RulesDoc(
    val title: String,
    val headerLabel: String,
    val asset: String,
    val url: String,
) {
    CR(
        title = "Comprehensive Rules",
        headerLabel = "COMPREHENSIVE RULES",
        asset = "fab-cr.txt",
        url = "https://rules.fabtcg.com/txt/latest/en-fab-cr.txt",
    ),
    TRP(
        title = "Tournament Rules & Policy",
        headerLabel = "TOURNAMENT RULES",
        asset = "fab-trp.txt",
        url = "https://rules.fabtcg.com/txt/latest/en-fab-trp.txt",
    ),
    PPG(
        title = "Penalty Guidelines",
        headerLabel = "PENALTY GUIDELINES",
        asset = "fab-ppg.txt",
        url = "https://rules.fabtcg.com/txt/latest/en-fab-ppg.txt",
    ),
}
