# 🎬 StreamVault – Web Application (JDBC + Security)

## 📌 Overview
StreamVault is a Java-based web application that simulates a content streaming platform. It allows users to register, browse content, manage subscriptions, and track viewing activity, while providing administrators with analytics and insights.

The project focuses on **secure database interaction, optimized SQL queries, and scalable connection management** using modern best practices.

---

## 🚀 Features

### 👤 User Features
- User registration & login with **bcrypt password hashing**
- Browse content with filters (genre, type, language)
- View detailed content (episodes, ratings, reviews)
- Watch content and track viewing history
- Manage subscription and billing details
- Personalized dashboard

### 🛠️ Admin Features
- Revenue analytics
- Top-performing content
- Genre popularity insights
- Churn risk detection
- User activity monitoring

---

## 🏗️ Tech Stack

- **Backend:** Java (Servlets, JDBC)
- **Database:** MySQL
- **Connection Pooling:** HikariCP
- **Security:** BCrypt (password hashing), PreparedStatements
- **Build Tool:** Maven
- **Frontend:** HTML, CSS

---

## 🔐 Security Implementation

- ✅ **Password Hashing:** BCrypt with cost factor 12  
- ✅ **SQL Injection Prevention:** PreparedStatement parameterization  
- ✅ **Role-Based Access Control (RBAC):** Viewer, Content Manager, Admin  
- ✅ **Secure Authentication Flow**

---

## ⚡ Database Optimization

- Complex queries using:
  - `JOIN`
  - `GROUP BY`
  - `HAVING`
  - `ORDER BY`
  - `LIMIT`
  - `DISTINCT`
  - Window functions (`RANK()`)

- Efficient data retrieval for analytics and dashboards

---

## 🔌 Connection Pooling (HikariCP)

This project uses **HikariCP** for efficient database connection management.

### Why?
- Avoids creating a new DB connection per request
- Improves performance and scalability
- Reduces latency

### Configuration Highlights:
- Max pool size: `10`
- Min idle connections: `2`
- Connection timeout: `20s`
- Idle timeout: `30s`

---

## 📂 Project Structure
```
src/main/java/com/streamvault/
│
├── db/
│ └── DatabaseConnection.java
│
├── servlets/
│ ├── LoginServlet.java
│ ├── RegisterServlet.java
│ ├── HomeServlet.java
│ ├── ContentDetailServlet.java
│ ├── DashboardServlet.java
│ └── AdminServlet.java
│
├── util/
│ └── AuthService.java
│
src/main/webapp/
├── index.html
├── login.html
├── register.html
├── home.html
├── content.html
├── dashboard.html
├── admin.html
└── css/style.css
```

---

## 🧪 Testing

- Full user journey tested:
  - Registration → Login → Browse → Watch → Dashboard → Logout
  - SQL queries logged with timestamps
  - Verified correct DB operations (INSERT, SELECT, JOIN, etc.)

---

## ▶️ How to Run

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/streamvault.git
   ```

2. Open in your IDE (IntelliJ / Eclipse)
3. Configure MySQL:
    Database name: streamvault
    Update credentials in DatabaseConnection.java if needed
4. Run the project on a servlet container (e.g., Tomcat)

### 📊 Key Learning Outcomes
 - Secure web application development
 - JDBC and database optimization
 - Connection pooling with HikariCP
 - Authentication & authorization
 - Writing complex SQL queries
 - Full-stack integration (frontend + backend + DB)

### 📎 Notes

This project was developed as part of a university coursework focusing on JDBC, security, and database performance optimization.

👨‍💻 Author

Mohammed Hesham
Yousef Qasim
Roland Daou


