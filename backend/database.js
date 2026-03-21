import sqlite3 from 'sqlite3';
import { promisify } from 'util';

const dbPath = process.env.DATABASE_PATH || './fall_detection.db';
let db;

export function initializeDatabase() {
    return new Promise((resolve, reject) => {
        db = new sqlite3.Database(dbPath, (err) => {
            if (err) {
                console.error('Database connection error:', err);
                reject(err);
            } else {
                console.log('Connected to SQLite database');
                createTables();
                resolve(db);
            }
        });
    });
}

function createTables() {
    const fallEventsTable = `
        CREATE TABLE IF NOT EXISTS fall_events (
            id TEXT PRIMARY KEY,
            timestamp INTEGER NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            confidence REAL NOT NULL,
            acceleration_magnitude REAL,
            gyroscope_magnitude REAL,
            tilt_angle REAL,
            sos_triggered INTEGER DEFAULT 0,
            maps_link TEXT,
            user_id TEXT,
            created_at INTEGER DEFAULT (strftime('%s', 'now'))
        )
    `;

    const emergencyContactsTable = `
        CREATE TABLE IF NOT EXISTS emergency_contacts (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            phone_number TEXT NOT NULL,
            email TEXT,
            is_active INTEGER DEFAULT 1,
            created_at INTEGER DEFAULT (strftime('%s', 'now'))
        )
    `;

    const alertsTable = `
        CREATE TABLE IF NOT EXISTS alerts (
            id TEXT PRIMARY KEY,
            event_id TEXT NOT NULL,
            contact_id TEXT NOT NULL,
            sms_sent INTEGER DEFAULT 0,
            call_triggered INTEGER DEFAULT 0,
            timestamp INTEGER DEFAULT (strftime('%s', 'now')),
            FOREIGN KEY (event_id) REFERENCES fall_events(id),
            FOREIGN KEY (contact_id) REFERENCES emergency_contacts(id)
        )
    `;

    db.run(fallEventsTable);
    db.run(emergencyContactsTable);
    db.run(alertsTable);

    console.log('Database tables initialized');
}

export function getDatabase() {
    return db;
}

// Promisified database operations
export function runQuery(sql, params = []) {
    return new Promise((resolve, reject) => {
        db.run(sql, params, function(err) {
            if (err) reject(err);
            else resolve({ lastID: this.lastID, changes: this.changes });
        });
    });
}

export function getQuery(sql, params = []) {
    return new Promise((resolve, reject) => {
        db.get(sql, params, (err, row) => {
            if (err) reject(err);
            else resolve(row);
        });
    });
}

export function allQuery(sql, params = []) {
    return new Promise((resolve, reject) => {
        db.all(sql, params, (err, rows) => {
            if (err) reject(err);
            else resolve(rows || []);
        });
    });
}
