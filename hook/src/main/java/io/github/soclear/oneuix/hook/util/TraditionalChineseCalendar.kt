package io.github.soclear.oneuix.hook.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object TraditionalChineseCalendar {
    /**
     * 1900-2049年
     * 每个 long 值编码了一年的农历信息:
     * 低4位 (0-3): 闰月月份 (0表示无闰月, 1-12表示闰几月)
     * 高12位 (4-15, 从低位到高位对应农历12月到1月): 每月是大月(30天, 为1)还是小月(29天, 为0)
     * 第16位: 如果有闰月，闰月是大月(1)还是小月(0)
     */
    private val INFO = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
    )

    private const val MIN_YEAR = 1900
    private val MAX_YEAR = MIN_YEAR + INFO.size - 1

    // 农历月份名称
    private val MONTH_NAMES = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")

    // 农历日期名称
    private val DAY_NAMES_PREFIX = arrayOf("初", "十", "廿", "三")
    private val DAY_NAMES_SUFFIX = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")


    // 基准日期：1900年正月初一（公历1900年1月31日）
    private val BASE_DATE = LocalDate.of(1900, 1, 31)

    /**
     * 返回农历y年的总天数
     */
    private fun getYearDays(year: Int): Int {
        var i = 0x8000
        // 12个小月 (29天) 的总天数
        var sum = 348
        val yearData = INFO[year - MIN_YEAR]
        // 检查从正月到腊月是否为大月，加上大月多出来的天数
        while (i > 0x8) {
            if ((yearData and i.toLong()) != 0L) {
                sum++
            }
            i = i shr 1
        }
        return sum + getLeapMonthDays(year)
    }


    /**
     * 返回农历y年闰月的天数 (没有闰月则返回0)
     */
    private fun getLeapMonthDays(year: Int): Int {
        return if (getLeapMonth(year) != 0) {
            if ((INFO[year - MIN_YEAR] and 0x10000L) != 0L) 30 else 29
        } else {
            0
        }
    }

    /**
     * 判断y年的农历中那个月是闰月, 不是闰月返回0 (1-12代表闰几月)
     */
    private fun getLeapMonth(year: Int): Int {
        return (INFO[year - MIN_YEAR] and 0xfL).toInt()
    }

    /**
     * 返回农历y年m月的总天数 (m是1-12，不考虑闰月本身的天数，而是指常规月份)
     */
    private fun getRegularMonthDays(year: Int, month: Int): Int {
        return if ((INFO[year - MIN_YEAR] and (0x10000L shr month)) != 0L) 30 else 29
    }

    /**
     * 格式化农历日
     */
    private fun formatDay(day: Int): String {
        return when (day) {
            // Invalid day
            !in 1..30 -> ""
            10 -> "初十"
            20 -> "二十"
            30 -> "三十"
            else -> DAY_NAMES_PREFIX[(day - 1) / 10] + DAY_NAMES_SUFFIX[(day - 1) % 10]
        }
    }

    /**
     * 获取指定公历日期的农历月份和日期。
     * @param gregorianDate 公历日期
     * @return "农历月份 农历日"，例如 "正月初一", "闰二月十五"。如果日期超出支持范围，则返回提示信息。
     */
    fun getMonthAndDay(gregorianDate: LocalDate = LocalDate.now()): String {
        val gregorianYear = gregorianDate.year
        if (gregorianYear !in MIN_YEAR..MAX_YEAR) {
            return "日期超出支持范围 ($MIN_YEAR-$MAX_YEAR)"
        }

        var offset = ChronoUnit.DAYS.between(BASE_DATE, gregorianDate)
        if (offset < 0) return "日期在1900年正月初一之前"


        var year = MIN_YEAR
        var daysInYear: Int
        while (year <= MAX_YEAR) {
            daysInYear = getYearDays(year)
            if (offset < daysInYear) {
                break
            }
            offset -= daysInYear
            year++
            // 如果超出了数据范围但offset还有剩余
            if (year > MAX_YEAR && offset >= 0) {
                return "日期超出数据支持的末尾"
            }
        }
        if (year > MAX_YEAR) return "日期超出数据支持的末尾(年份计算溢出)"


        var monthResult = 0
        var dayResult = 0
        var isLeapMonthResult = false

        // 闰几月 (1-12), 0为不闰
        val leapMonthCode = getLeapMonth(year)

        // 遍历12个常规月份
        for (month in 1..12) {
            // 处理当前常规月份
            val daysInCurrentRegularMonth = getRegularMonthDays(year, month)
            if (offset < daysInCurrentRegularMonth) {
                monthResult = month
                dayResult = (offset + 1).toInt()
                isLeapMonthResult = false
                break
            }
            offset -= daysInCurrentRegularMonth

            // 如果当前常规月份之后是闰月
            if (leapMonthCode == month) {
                val daysInLeap = getLeapMonthDays(year)
                if (offset < daysInLeap) {
                    // 闰月的月份号与它所闰的月份相同
                    monthResult = month
                    dayResult = (offset + 1).toInt()
                    isLeapMonthResult = true
                    break
                }
                offset -= daysInLeap
            }
        }

        // 理论上不应发生，除非offset计算有误或日期恰好在年边界
        if (monthResult == 0) {
            return "农历日期计算错误"
        }

        val monthDisplayName = (if (isLeapMonthResult) "闰" else "") + MONTH_NAMES[monthResult - 1]
        val dayDisplayName = formatDay(dayResult)

        return "${monthDisplayName}月$dayDisplayName"
    }
}
