# Simple Chat App (Java + SQLite + Swing)

A desktop chat application built in pure **Java Swing** with **SQLite** persistence.  
It supports user accounts, one-to-one messaging, and an "About" tab.

---

## ✨ Features
- **Sign Up / Login**
- **List of registered users**
- **One-to-one chat** with any registered user
- **Message history** stored in SQLite
- **Auto-refresh (polling every 2s)**
- **About page** with an auto-generated author image
- Ships with demo users (`alice`, `bob`, `carol`)

---

## ⚙️ Requirements
- Java JDK 8 or higher
- [SQLite JDBC driver](https://github.com/xerial/sqlite-jdbc) (e.g., `sqlite-jdbc-3.46.0.0.jar`)
- SQLite command-line tools (optional, for inspecting the database)

---

## 🚀 Build & Run

1. Place these files in the same folder:
   - `ChatApp.java`
   - `sqlite-jdbc-3.46.0.0.jar`

2. Compile:

   ```powershell
   javac -cp ".;sqlite-jdbc-3.46.0.0.jar" ChatApp.java
