package com.example.tripplanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "TripPlannerDB";
    private static final int DB_VERSION = 1;

    // Trip history table
    public static final String TABLE_TRIPS = "trips";
    public static final String COL_ID = "id";
    public static final String COL_DESTINATION = "destination";
    public static final String COL_START_DATE = "start_date";
    public static final String COL_END_DATE = "end_date";
    public static final String COL_ACTIVITIES = "activities";
    public static final String COL_CREATED_AT = "created_at";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_TRIPS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DESTINATION + " TEXT NOT NULL, " +
                COL_START_DATE + " INTEGER, " +
                COL_END_DATE + " INTEGER, " +
                COL_ACTIVITIES + " TEXT, " +
                COL_CREATED_AT + " INTEGER DEFAULT (strftime('%s','now'))" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRIPS);
        onCreate(db);
    }

    // Insert a trip
    public long insertTrip(String destination, long startDate, long endDate, String activities) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_DESTINATION, destination);
        cv.put(COL_START_DATE, startDate);
        cv.put(COL_END_DATE, endDate);
        cv.put(COL_ACTIVITIES, activities);
        long id = db.insert(TABLE_TRIPS, null, cv);
        db.close();
        return id;
    }

    // Get all trips ordered by newest first
    public List<TripRecord> getAllTrips() {
        List<TripRecord> trips = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRIPS, null, null, null, null, null, COL_CREATED_AT + " DESC");
        if (c.moveToFirst()) {
            do {
                TripRecord t = new TripRecord();
                t.id = c.getInt(c.getColumnIndexOrThrow(COL_ID));
                t.destination = c.getString(c.getColumnIndexOrThrow(COL_DESTINATION));
                t.startDate = c.getLong(c.getColumnIndexOrThrow(COL_START_DATE));
                t.endDate = c.getLong(c.getColumnIndexOrThrow(COL_END_DATE));
                t.activities = c.getString(c.getColumnIndexOrThrow(COL_ACTIVITIES));
                trips.add(t);
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return trips;
    }

    // Delete a trip by id
    public void deleteTrip(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_TRIPS, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public static class TripRecord {
        public int id;
        public String destination;
        public long startDate;
        public long endDate;
        public String activities;
    }
}
