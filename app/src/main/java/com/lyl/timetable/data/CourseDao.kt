package com.lyl.timetable.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

/** 课程表数据访问对象 */
internal class CourseDao(context: Context) {

    private val helper = DbHelper(context.applicationContext)

    fun all(): List<Course> {
        val out = ArrayList<Course>()
        helper.readableDatabase.query(
            DbHelper.TABLE_COURSE, null, null, null,
            null, null, "day_of_week ASC, start_section ASC, name ASC"
        ).use { c ->
            while (c.moveToNext()) out.add(c.toCourse())
        }
        return out
    }

    fun insert(course: Course): Long {
        val values = course.toValues(includeId = false)
        values.put("updated_at", System.currentTimeMillis())
        return helper.writableDatabase.insert(DbHelper.TABLE_COURSE, null, values)
    }

    fun insertAll(courses: List<Course>): Int {
        if (courses.isEmpty()) return 0
        val db = helper.writableDatabase
        db.beginTransaction()
        return try {
            var count = 0
            for (c in courses) {
                val values = c.toValues(includeId = false)
                values.put("updated_at", System.currentTimeMillis())
                if (db.insert(DbHelper.TABLE_COURSE, null, values) > 0) count++
            }
            db.setTransactionSuccessful()
            count
        } finally {
            db.endTransaction()
        }
    }

    fun update(course: Course): Boolean {
        if (course.id <= 0) return false
        val values = course.toValues(includeId = false)
        values.put("updated_at", System.currentTimeMillis())
        return helper.writableDatabase.update(
            DbHelper.TABLE_COURSE, values, "id = ?", arrayOf(course.id.toString())
        ) > 0
    }

    fun delete(id: Long): Boolean =
        helper.writableDatabase.delete(DbHelper.TABLE_COURSE, "id = ?", arrayOf(id.toString())) > 0

    fun deleteAll(): Int = helper.writableDatabase.delete(DbHelper.TABLE_COURSE, null, null)

    fun count(): Int {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${DbHelper.TABLE_COURSE}", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun Course.toValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId && id > 0) put("id", id)
        put("name", name)
        put("teacher", teacher)
        put("location", location)
        put("day_of_week", dayOfWeek)
        put("start_section", startSection)
        put("end_section", endSection)
        put("week_mask", weekMask)
        put("color_index", colorIndex)
        put("note", note)
    }

    private fun Cursor.toCourse(): Course = Course(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")) ?: "",
        teacher = getString(getColumnIndexOrThrow("teacher")) ?: "",
        location = getString(getColumnIndexOrThrow("location")) ?: "",
        dayOfWeek = getInt(getColumnIndexOrThrow("day_of_week")),
        startSection = getInt(getColumnIndexOrThrow("start_section")),
        endSection = getInt(getColumnIndexOrThrow("end_section")),
        weekMask = getInt(getColumnIndexOrThrow("week_mask")),
        colorIndex = getInt(getColumnIndexOrThrow("color_index")),
        note = getString(getColumnIndexOrThrow("note")) ?: ""
    )
}
