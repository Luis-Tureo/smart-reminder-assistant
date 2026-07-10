package com.luistureo.voicereminderapp.domain.routine.factory

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplateTask

data class RoutineDraft(val routine: Routine, val tasks: List<RoutineTask>)

object DefaultRoutineFactory {
    fun create(nowEpochMillis: Long): List<RoutineDraft> = listOf(
        draft(
            nowEpochMillis,
            "Rutina de la mañana",
            "Organiza el inicio del día.",
            RoutinePeriod.MORNING,
            "wb_sunny",
            0xFFFFB300.toInt(),
            listOf("Beber agua", "Tomar medicamentos", "Desayunar", "Prepararse para el día")
        ),
        draft(
            nowEpochMillis,
            "Rutina de la tarde",
            "Mantén el foco durante la tarde.",
            RoutinePeriod.AFTERNOON,
            "light_mode",
            0xFFFF8F00.toInt(),
            listOf("Revisar pendientes", "Realizar actividad física", "Organizar actividades")
        ),
        draft(
            nowEpochMillis,
            "Rutina de la noche",
            "Cierra el día con tranquilidad.",
            RoutinePeriod.NIGHT,
            "bedtime",
            0xFF3949AB.toInt(),
            listOf(
                "Preparar el día siguiente",
                "Realizar higiene personal",
                "Revisar la agenda",
                "Prepararse para dormir"
            )
        )
    )

    private fun draft(
        nowEpochMillis: Long,
        name: String,
        description: String,
        period: RoutinePeriod,
        icon: String,
        color: Int,
        taskTitles: List<String>
    ) = RoutineDraft(
        routine = Routine(
            name = name,
            description = description,
            category = "Rutinas del día",
            icon = icon,
            color = color,
            enabled = true,
            period = period,
            assistantMode = RoutineAssistantMode.SIMPLE_DISPLAY,
            voiceEnabled = false,
            motivationBubbleEnabled = true,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis
        ),
        tasks = taskTitles.mapIndexed { index, title ->
            RoutineTask(title = title, orderPriority = index)
        }
    )
}

object DefaultRoutineTemplateFactory {
    fun create(): List<RoutineTemplate> = listOf(
        template("morning_organized", "Mañana organizada", RoutinePeriod.MORNING,
            "Organiza las primeras actividades del día en pasos simples y predecibles.",
            "Puede ayudar a reducir olvidos y facilitar la preparación antes de salir.",
            "Organización", 35, listOf("Levantarse", "Revisar agenda", "Desayunar", "Prepararse para salir")),
        template("morning_calm", "Mañana tranquila", RoutinePeriod.MORNING,
            "Propone un inicio del día con pocas transiciones y tiempos claros.",
            "Puede facilitar una preparación gradual y predecible.",
            "Descanso", 30, listOf("Beber agua", "Desayunar", "Prepararse con calma")),
        template("leaving_home", "Prepararse para salir", RoutinePeriod.MORNING,
            "Reúne en orden los pasos habituales antes de salir.",
            "Puede ayudar a comprobar objetos importantes y reducir olvidos.",
            "Prepararse para salir", 25, listOf("Vestirse", "Revisar llaves y teléfono", "Preparar bolso", "Salir")),
        template("healthy", "Rutina saludable", RoutinePeriod.MORNING,
            "Combina hidratación, alimentación y movimiento suave.",
            "Puede facilitar hábitos cotidianos de autocuidado. Adáptala a tus necesidades.",
            "Salud y autocuidado", 35, listOf("Beber agua", "Desayunar", "Movimiento suave")),
        template("study", "Organización para estudiar", RoutinePeriod.AFTERNOON,
            "Divide la preparación del estudio en instrucciones claras.",
            "Puede ayudar a ordenar materiales, prioridades y pausas.",
            "Estudio", 60, listOf("Preparar materiales", "Elegir una prioridad", "Estudiar", "Registrar avance")),
        template("productive_afternoon", "Tarde productiva", RoutinePeriod.AFTERNOON,
            "Ordena pendientes importantes y una pausa breve.",
            "Puede facilitar el enfoque sin sobrecargar la jornada.",
            "Trabajo", 75, listOf("Revisar pendientes", "Completar prioridad", "Hacer una pausa", "Ordenar cierre")),
        template("self_care", "Pausa y autocuidado", RoutinePeriod.AFTERNOON,
            "Ofrece pasos breves para hacer una pausa consciente.",
            "Puede ayudar a recordar necesidades básicas durante el día.",
            "Salud y autocuidado", 20, listOf("Pausar actividad", "Beber agua", "Comer algo", "Respirar con calma")),
        template("hygiene", "Higiene personal", RoutinePeriod.NIGHT,
            "Presenta actividades de higiene en una secuencia editable.",
            "Puede ayudar a seguir pasos predecibles y claros.",
            "Higiene", 20, listOf("Lavarse los dientes", "Lavarse la cara", "Preparar ropa")),
        template("calm_night", "Noche tranquila", RoutinePeriod.NIGHT,
            "Propone una transición gradual hacia el descanso.",
            "Puede facilitar un cierre del día con menos decisiones.",
            "Descanso", 35, listOf("Reducir estímulos", "Higiene personal", "Prepararse para dormir")),
        template("next_day", "Preparar el día siguiente", RoutinePeriod.NIGHT,
            "Agrupa los preparativos básicos para la jornada siguiente.",
            "Puede ayudar a anticipar tareas y objetos necesarios.",
            "Organización", 20, listOf("Revisar agenda", "Preparar ropa", "Preparar bolso", "Definir primera actividad")),
        template("visual_steps", "Rutina visual paso a paso", RoutinePeriod.MORNING,
            "Usa una secuencia corta para personas que prefieren instrucciones claras.",
            "Puede ayudar a seguir pasos predecibles. Todos pueden editarse.",
            "Apoyo visual", 30, listOf("Mirar el primer paso", "Completar una actividad", "Marcarla", "Continuar")),
        template("difficult_day", "Rutina con pocas tareas para días difíciles", RoutinePeriod.MORNING,
            "Incluye solo actividades esenciales y fáciles de revisar.",
            "Una rutina más corta puede facilitar comenzar. Adáptala según tus necesidades.",
            "Rutina simplificada", 15, listOf("Beber agua", "Comer algo", "Elegir una actividad"))
    )

    private fun template(
        key: String,
        name: String,
        period: RoutinePeriod,
        description: String,
        benefits: String,
        category: String,
        duration: Int,
        tasks: List<String>
    ) = RoutineTemplate(
        name = name,
        description = description,
        benefitsExplanation = benefits,
        period = period,
        estimatedTotalDurationMinutes = duration,
        icon = when (period) {
            RoutinePeriod.MORNING -> "wb_sunny"
            RoutinePeriod.AFTERNOON -> "light_mode"
            RoutinePeriod.NIGHT -> "bedtime"
        },
        category = category,
        editable = false,
        builtIn = true,
        builtInKey = key,
        suggestedTasks = tasks.mapIndexed { index, title ->
            RoutineTemplateTask(
                title = title,
                orderPriority = index,
                estimatedDurationMinutes = (duration / tasks.size).coerceAtLeast(1)
            )
        }
    )
}
