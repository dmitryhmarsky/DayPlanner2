package com.example.dayplanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** date — в формате ISO: 2026-10-30; time — "HH:mm" или пустая строка (без времени). */
data class Event(
    val id: Long,
    val date: String,
    val time: String,
    val title: String,
    val description: String
)

class Db private constructor(context: Context) :
    SQLiteOpenHelper(context, "planner.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                time TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL("CREATE INDEX idx_events_date ON events(date)")
        db.execSQL("CREATE TABLE day_notes (date TEXT PRIMARY KEY, note TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** События дня: сначала со временем (по возрастанию), затем без времени. */
    fun eventsFor(date: String): List<Event> {
        val out = ArrayList<Event>()
        readableDatabase.rawQuery(
            "SELECT id, time, title, description FROM events WHERE date = ? ORDER BY (time = ''), time, id",
            arrayOf(date)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Event(c.getLong(0), date, c.getString(1), c.getString(2), c.getString(3)))
            }
        }
        return out
    }

    /** id == 0 — новое событие, иначе обновление. */
    fun saveEvent(e: Event) {
        val v = ContentValues().apply {
            put("date", e.date)
            put("time", e.time)
            put("title", e.title)
            put("description", e.description)
        }
        if (e.id == 0L) {
            writableDatabase.insert("events", null, v)
        } else {
            writableDatabase.update("events", v, "id = ?", arrayOf(e.id.toString()))
        }
    }

    /** События за период, сгруппированные по дате (для плашек в ячейках месяца). */
    fun eventsInRange(from: String, to: String): Map<String, List<Event>> {
        val out = LinkedHashMap<String, MutableList<Event>>()
        readableDatabase.rawQuery(
            "SELECT id, date, time, title, description FROM events " +
                "WHERE date BETWEEN ? AND ? ORDER BY date, (time = ''), time, id",
            arrayOf(from, to)
        ).use { c ->
            while (c.moveToNext()) {
                val e = Event(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
                out.getOrPut(e.date) { ArrayList() }.add(e)
            }
        }
        return out
    }

    /** Заметки за период: дата -> текст. */
    fun notesInRange(from: String, to: String): Map<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery(
            "SELECT date, note FROM day_notes WHERE date BETWEEN ? AND ?",
            arrayOf(from, to)
        ).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    fun deleteEvent(id: Long) {
        writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))
    }

    fun noteFor(date: String): String =
        readableDatabase.rawQuery("SELECT note FROM day_notes WHERE date = ?", arrayOf(date))
            .use { if (it.moveToFirst()) it.getString(0) else "" }

    /** Пустая заметка удаляется. */
    fun saveNote(date: String, note: String) {
        if (note.isBlank()) {
            writableDatabase.delete("day_notes", "date = ?", arrayOf(date))
        } else {
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO day_notes(date, note) VALUES(?, ?)",
                arrayOf(date, note)
            )
        }
    }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context.applicationContext).also { instance = it }
            }
    }
}
