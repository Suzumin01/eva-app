package com.eva.app.util

/**
 * Единый источник данных о специализациях.
 * Используется в:
 *  - DoctorsScreen (фильтр-дропдаун)
 *  - SpecializationsScreen (список карточек)
 *  - MainActivity (маппинг AI-ответа → specId для навигации)
 */
data class SpecializationItem(
    val id: Int,
    val name: String,
    val description: String,
    /** Альтернативные названия, которые может вернуть AI-сервис */
    val altNames: List<String> = emptyList()
)

object Specializations {

    val all = listOf(
        SpecializationItem(1, "Терапевт",    "Первичная медицинская помощь",     listOf("Терапия", "Гастроэнтерология")),
        SpecializationItem(2, "Кардиолог",   "Сердечно-сосудистая система",      listOf("Кардиология")),
        SpecializationItem(3, "Невролог",    "Нервная система",                  listOf("Неврология")),
        SpecializationItem(4, "Ортопед",     "Опорно-двигательный аппарат",      listOf("Ортопедия")),
        SpecializationItem(5, "Психолог",    "Психологическая помощь",           listOf("Психология")),
        SpecializationItem(6, "ЛОР",         "Ухо, горло, нос",                  emptyList()),
        SpecializationItem(7, "Педиатр",     "Лечение детей",                    listOf("Педиатрия")),
        SpecializationItem(8, "Дерматолог",  "Кожные заболевания",               listOf("Дерматология"))
    )

    /** Список для дропдауна фильтра врачей (первый элемент — "Все") */
    val forFilter: List<Pair<Int?, String>> =
        listOf(null to "Все") + all.map { it.id to it.name }

    /**
     * Находит id специализации по любому из её названий (canonical + alt).
     * Используется для навигации из AI-результата → экран врачей.
     */
    fun findIdByName(name: String): Int? =
        all.find { spec ->
            spec.name.equals(name, ignoreCase = true) ||
                    spec.altNames.any { it.equals(name, ignoreCase = true) }
        }?.id
}