package com.lyl.timetable.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地数据库（应用私有目录，完全离线）。
 * 仅一张课程表，结构简单，故直接使用系统 SQLite，无需引入 ORM。
 */
internal class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_COURSE (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                name          TEXT    NOT NULL,
                teacher       TEXT    NOT NULL DEFAULT '',
                location      TEXT    NOT NULL DEFAULT '',
                day_of_week   INTEGER NOT NULL,
                start_section INTEGER NOT NULL,
                end_section   INTEGER NOT NULL,
                week_mask     INTEGER NOT NULL DEFAULT 0,
                color_index   INTEGER NOT NULL DEFAULT 0,
                note          TEXT    NOT NULL DEFAULT '',
                updated_at    INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_course_day ON $TABLE_COURSE(day_of_week)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 数据结构保持向后兼容，仅在破坏性变更时重建
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE $TABLE_COURSE ADD COLUMN note TEXT NOT NULL DEFAULT ''") }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val DB_NAME = "timetable.db"
        const val DB_VERSION = 2
        const val TABLE_COURSE = "course"
    }
}
