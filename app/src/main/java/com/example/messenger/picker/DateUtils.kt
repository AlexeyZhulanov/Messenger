package com.example.messenger.picker

import android.annotation.SuppressLint
import android.content.Context
import com.luck.picture.lib.R
import com.luck.picture.lib.utils.ValueOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object DateUtils {
    @SuppressLint("SimpleDateFormat")
    private val SF = SimpleDateFormat("yyyyMMddHHmmssSSS")

    @SuppressLint("SimpleDateFormat")
    private val SDF = SimpleDateFormat("yyyy-MM")

    @SuppressLint("SimpleDateFormat")
    private val SDF_YEAR = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    val currentTimeMillis: Long
        get() {
            val timeToString = ValueOf.toString(System.currentTimeMillis())
            return ValueOf.toLong(
                if (timeToString.length > 10) timeToString.substring(
                    0,
                    10
                ) else timeToString
            )
        }


    fun getDataFormat(context: Context, time1: Long): String {
        var time = time1
        time = if (time.toString().length > 10) time else time * 1000
        return if (isThisWeek(time)) {
            context.getString(R.string.ps_current_week)
        } else if (isThisMonth(time)) {
            context.getString(R.string.ps_current_month)
        } else {
            SDF.format(time)
        }
    }

    fun getYearDataFormat(time1: Long): String {
        var time = time1
        time = if (time.toString().length > 10) time else time * 1000
        return SDF_YEAR.format(time)
    }

    private fun isThisWeek(time: Long): Boolean {
        val calendar = Calendar.getInstance()
        val currentWeek = calendar[Calendar.WEEK_OF_YEAR]
        calendar.time = Date(time)
        val paramWeek = calendar[Calendar.WEEK_OF_YEAR]
        return paramWeek == currentWeek
    }

    fun isThisMonth(time: Long): Boolean {
        val date = Date(time)
        val param = SDF.format(date)
        val now = SDF.format(Date())
        return param == now
    }


    /**
     * millisecondToSecond
     *
     * @param duration millisecond
     * @return
     */
    fun millisecondToSecond(duration: Long): Long {
        return (duration / 1000) * 1000
    }

    /**
     * 判断两个时间戳相差多少秒
     *
     * @param d
     * @return
     */
    fun dateDiffer(d: Long): Int {
        try {
            val l1 = currentTimeMillis
            val interval = l1 - d
            return abs(interval.toDouble()).toInt()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    /**
     * 时间戳转换成时间格式
     *
     * @param timeMs
     * @return
     */
    fun formatDurationTime(timeMs1: Long): String {
        var timeMs = timeMs1
        val prefix = if (timeMs < 0) "-" else ""
        timeMs = abs(timeMs.toDouble()).toLong()
        val totalSeconds = timeMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0
        ) String.format(
            Locale.getDefault(),
            "%s%d:%02d:%02d",
            prefix,
            hours,
            minutes,
            seconds
        ) else String.format(
            Locale.getDefault(), "%s%02d:%02d", prefix, minutes, seconds
        )
    }


    /**
     * 根据时间戳创建文件名
     *
     * @param prefix 前缀名
     * @return
     */
    fun getCreateFileName(prefix: String): String {
        val millis = System.currentTimeMillis()
        return prefix + SF.format(millis)
    }

    val createFileName: String
        /**
         * 根据时间戳创建文件名
         *
         * @return
         */
        get() {
            val millis = System.currentTimeMillis()
            return SF.format(millis)
        }

    /**
     * 计算两个时间间隔
     *
     * @param sTime
     * @param eTime
     * @return
     */
    fun cdTime(sTime: Long, eTime: Long): String {
        val diff = eTime - sTime
        return if (diff > 1000) (diff / 1000).toString() + "秒" else diff.toString() + "毫秒"
    }
}