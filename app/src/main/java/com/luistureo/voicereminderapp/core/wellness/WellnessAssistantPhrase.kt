package com.luistureo.voicereminderapp.core.wellness

enum class WellnessAssistantPhrase(
    internal val catalogText: String
) {
    NUTRITION_REVIEW_BREAKFAST("Es hora de revisar tu desayuno."),
    NUTRITION_LUNCH_PLANNED("Tu almuerzo está planificado para hoy."),
    NUTRITION_DAY_PLANNING_COMPLETED("Has completado tu planificación del día."),
    NUTRITION_ADJUST_PLAN("Puedes ajustar el plan para que sea más fácil de seguir."),
    RECOVERY_REMEMBER_REASON("Recuerda por qué comenzaste."),
    RECOVERY_POSITIVE_DECISIONS("Cada decisión positiva cuenta."),
    RECOVERY_REVIEW_SUPPORT_TOOLS("Puedes revisar tus herramientas de apoyo."),
    RECOVERY_DIFFICULT_MOMENT("Un momento difícil no elimina tu progreso.")
}
