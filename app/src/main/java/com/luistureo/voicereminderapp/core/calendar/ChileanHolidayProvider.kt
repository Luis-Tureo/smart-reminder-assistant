package com.luistureo.voicereminderapp.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class ChileanHoliday(
    val date: LocalDate,
    val name: String
)

class ChileanHolidayProvider {

    private val holidayCache = mutableMapOf<Int, Map<LocalDate, List<ChileanHoliday>>>()

    private val indigenousHolidayOverrides = mapOf(
        2021 to 21,
        2022 to 21,
        2023 to 21,
        2024 to 20,
        2025 to 20,
        2026 to 21,
        2027 to 21,
        2028 to 20,
        2029 to 20,
        2030 to 21,
        2031 to 21,
        2032 to 20,
        2033 to 20
    )

    fun getHolidays(date: LocalDate): List<ChileanHoliday> {
        return getHolidaysForYear(date.year)[date].orEmpty()
    }

    fun getHolidaysForMonth(yearMonth: YearMonth): Map<LocalDate, List<ChileanHoliday>> {
        return getHolidaysForYear(yearMonth.year)
            .filterKeys { YearMonth.from(it) == yearMonth }
    }

    fun getHolidaysForYear(year: Int): Map<LocalDate, List<ChileanHoliday>> {
        return holidayCache.getOrPut(year) {
            buildHolidays(year).groupBy { it.date }
        }
    }

    private fun buildHolidays(year: Int): List<ChileanHoliday> {
        val easterSunday = calculateEasterSunday(year)
        val holidays = mutableListOf<ChileanHoliday>()

        fun addHoliday(date: LocalDate, name: String) {
            holidays += ChileanHoliday(date = date, name = name)
        }

        val januarySecond = LocalDate.of(year, 1, 2)
        val septemberSeventeenth = LocalDate.of(year, 9, 17)
        val septemberTwentieth = LocalDate.of(year, 9, 20)

        addHoliday(LocalDate.of(year, 1, 1), "Año Nuevo")
        if (januarySecond.dayOfWeek == DayOfWeek.MONDAY) {
            addHoliday(januarySecond, "Feriado adicional de Año Nuevo")
        }

        addHoliday(easterSunday.minusDays(2), "Viernes Santo")
        addHoliday(easterSunday.minusDays(1), "Sábado Santo")
        addHoliday(LocalDate.of(year, 5, 1), "Día del Trabajo")
        addHoliday(LocalDate.of(year, 5, 21), "Día de las Glorias Navales")
        addHoliday(resolveIndigenousHoliday(year), "Día Nacional de los Pueblos Indígenas")
        addHoliday(
            observeMondayHoliday(LocalDate.of(year, 6, 29)),
            "San Pedro y San Pablo"
        )
        addHoliday(LocalDate.of(year, 7, 16), "Virgen del Carmen")
        addHoliday(LocalDate.of(year, 8, 15), "Asunción de la Virgen")

        if (
            septemberSeventeenth.dayOfWeek == DayOfWeek.MONDAY ||
            septemberSeventeenth.dayOfWeek == DayOfWeek.FRIDAY
        ) {
            addHoliday(septemberSeventeenth, "Feriado adicional de Fiestas Patrias")
        }

        if (septemberTwentieth.dayOfWeek == DayOfWeek.FRIDAY) {
            addHoliday(septemberTwentieth, "Feriado adicional de Fiestas Patrias")
        }

        addHoliday(LocalDate.of(year, 9, 18), "Independencia Nacional")
        addHoliday(LocalDate.of(year, 9, 19), "Día de las Glorias del Ejército")
        addHoliday(
            observeMondayHoliday(LocalDate.of(year, 10, 12)),
            "Encuentro de Dos Mundos"
        )
        addHoliday(
            observeReformationDay(LocalDate.of(year, 10, 31)),
            "Día de las Iglesias Evangélicas y Protestantes"
        )
        addHoliday(LocalDate.of(year, 11, 1), "Día de Todos los Santos")
        addHoliday(LocalDate.of(year, 12, 8), "Inmaculada Concepción")
        addHoliday(LocalDate.of(year, 12, 25), "Navidad")

        return holidays
            .distinctBy { it.date to it.name }
            .sortedBy { it.date }
    }

    private fun resolveIndigenousHoliday(year: Int): LocalDate {
        val day = indigenousHolidayOverrides[year] ?: 21
        return LocalDate.of(year, 6, day)
    }

    private fun observeMondayHoliday(date: LocalDate): LocalDate {
        return when (date.dayOfWeek) {
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY -> date.with(TemporalAdjusters.previous(DayOfWeek.MONDAY))

            DayOfWeek.FRIDAY -> date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            else -> date
        }
    }

    private fun observeReformationDay(date: LocalDate): LocalDate {
        return when (date.dayOfWeek) {
            DayOfWeek.TUESDAY -> date.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
            DayOfWeek.WEDNESDAY -> date.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
            else -> date
        }
    }

    private fun calculateEasterSunday(year: Int): LocalDate {
        val century = year / 100
        val yearOfCentury = year % 100
        val leapCentury = century / 4
        val centuryRemainder = century % 4
        val correction = (century + 8) / 25
        val adjustedCentury = (century - correction + 1) / 3
        val goldenYear = (19 * (year % 19) + century - leapCentury - adjustedCentury + 15) % 30
        val leapYearOfCentury = yearOfCentury / 4
        val yearRemainder = yearOfCentury % 4
        val weekdayOffset = (32 + 2 * centuryRemainder + 2 * leapYearOfCentury - goldenYear - yearRemainder) % 7
        val monthFactor = (year % 19 + 11 * goldenYear + 22 * weekdayOffset) / 451
        val month = (goldenYear + weekdayOffset - 7 * monthFactor + 114) / 31
        val day = (goldenYear + weekdayOffset - 7 * monthFactor + 114) % 31 + 1

        return LocalDate.of(year, month, day)
    }
}
