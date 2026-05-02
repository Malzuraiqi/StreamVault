package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import com.streamvault.util.QueryLogger;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

import com.mongodb.client.*;
import org.bson.Document;
import java.util.Arrays;

@WebServlet("/admin")
public class AdminServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || !"admin".equals(session.getAttribute("role"))) {
            resp.setStatus(403);
            out.print(new JSONObject().put("error", "Access denied. Admins only."));
            return;
        }

        JSONObject response = new JSONObject();

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1. Revenue by plan this month
            String sqlRevenue =
                    "SELECT sp.plan_name, COUNT(p.payment_id) AS payment_count, " +
                            "SUM(p.amount) AS total_revenue, AVG(p.amount) AS avg_payment " +
                            "FROM Payments p " +
                            "JOIN Subscriptions s ON p.subscription_id = s.subscription_id " +
                            "JOIN Subscription_Plans sp ON s.plan_id = sp.plan_id " +
                            "WHERE p.status = 'completed' " +
                            "AND MONTH(p.payment_date) = MONTH(NOW()) " +
                            "AND YEAR(p.payment_date) = YEAR(NOW()) " +
                            "GROUP BY sp.plan_name ORDER BY total_revenue DESC";
            JSONArray revenue = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlRevenue)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject r = new JSONObject();
                    r.put("plan_name",      rs.getString("plan_name"));
                    r.put("payment_count",  rs.getInt("payment_count"));
                    r.put("total_revenue",  rs.getDouble("total_revenue"));
                    r.put("avg_payment",    rs.getDouble("avg_payment"));
                    revenue.put(r);
                }
                QueryLogger.log("AdminServlet", "SELECT Revenue GROUP BY", sqlRevenue, revenue.length());
            }
            response.put("revenue_by_plan", revenue);

            // 2. New subscribers this month
            String sqlNewSubs =
                    "SELECT COUNT(*) AS new_subscribers " +
                            "FROM Subscriptions " +
                            "WHERE start_date >= DATE_SUB(NOW(), INTERVAL 30 DAY)";
            try (PreparedStatement ps = conn.prepareStatement(sqlNewSubs)) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) response.put("new_subscribers", rs.getInt("new_subscribers"));
                QueryLogger.log("AdminServlet", "SELECT New Subscribers COUNT", sqlNewSubs);
            }

            // 3. Top 10 content by completion rate
            String sqlTop10 =
                    "SELECT ci.title, ci.type, " +
                            "COUNT(wh.history_id) AS total_views, " +
                            "SUM(wh.completed) AS completed_views, " +
                            "ROUND(SUM(wh.completed) * 100.0 / COUNT(wh.history_id), 2) AS completion_rate " +
                            "FROM Watch_History wh " +
                            "JOIN Content_Items ci ON wh.content_id = ci.content_id " +
                            "GROUP BY ci.content_id, ci.title, ci.type " +
                            "HAVING COUNT(wh.history_id) > 0 " +
                            "ORDER BY completion_rate DESC LIMIT 10";
            JSONArray top10 = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlTop10)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject t = new JSONObject();
                    t.put("title",           rs.getString("title"));
                    t.put("type",            rs.getString("type"));
                    t.put("total_views",     rs.getInt("total_views"));
                    t.put("completed_views", rs.getInt("completed_views"));
                    t.put("completion_rate", rs.getDouble("completion_rate"));
                    top10.put(t);
                }
                QueryLogger.log("AdminServlet", "SELECT Top10 Completion", sqlTop10, top10.length());
            }
            response.put("top10_content", top10);

            // 4. Genre popularity
            String sqlGenre =
                    "SELECT g.name AS genre, COUNT(wh.history_id) AS watch_count " +
                            "FROM Watch_History wh " +
                            "JOIN Content_Items ci ON wh.content_id = ci.content_id " +
                            "JOIN Content_Genre cg ON ci.content_id = cg.content_id " +
                            "JOIN Genres g ON cg.genre_id = g.genre_id " +
                            "GROUP BY g.name ORDER BY watch_count DESC";
            JSONArray genrePopularity = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlGenre)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject g = new JSONObject();
                    g.put("genre",       rs.getString("genre"));
                    g.put("watch_count", rs.getInt("watch_count"));
                    genrePopularity.put(g);
                }
                QueryLogger.log("AdminServlet", "SELECT Genre Popularity", sqlGenre, genrePopularity.length());
            }
            response.put("genre_popularity", genrePopularity);

            // 5. Total platform stats
            String sqlStats =
                    "SELECT " +
                            "(SELECT COUNT(*) FROM Users WHERE is_active = 1) AS active_users, " +
                            "(SELECT COUNT(*) FROM Content_Items) AS total_content, " +
                            "(SELECT COUNT(*) FROM Subscriptions WHERE status = 'active') AS active_subscriptions, " +
                            "(SELECT SUM(amount) FROM Payments WHERE status = 'completed') AS total_revenue";
            try (PreparedStatement ps = conn.prepareStatement(sqlStats)) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    JSONObject stats = new JSONObject();
                    stats.put("active_users",          rs.getInt("active_users"));
                    stats.put("total_content",         rs.getInt("total_content"));
                    stats.put("active_subscriptions",  rs.getInt("active_subscriptions"));
                    stats.put("total_revenue",         rs.getDouble("total_revenue"));
                    response.put("platform_stats", stats);
                }
            }

            String sqlChurn =
                    "SELECT u.full_name, u.email, u.country, " +
                            "MAX(wh.watch_date) AS last_watch, " +
                            "DATEDIFF(NOW(), MAX(wh.watch_date)) AS days_inactive " +
                            "FROM Users u " +
                            "JOIN Watch_History wh ON u.user_id = wh.user_id " +
                            "JOIN Subscriptions s ON u.user_id = s.user_id " +
                            "WHERE s.status = 'active' " +
                            "GROUP BY u.user_id, u.full_name, u.email, u.country " +
                            "HAVING days_inactive > 30 " +
                            "ORDER BY days_inactive DESC";
            JSONArray churn = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlChurn)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject c = new JSONObject();
                    c.put("full_name",     rs.getString("full_name"));
                    c.put("email",         rs.getString("email"));
                    c.put("country",       rs.getString("country"));
                    c.put("last_watch",    rs.getString("last_watch"));
                    c.put("days_inactive", rs.getInt("days_inactive"));
                    churn.put(c);
                }
                QueryLogger.log("AdminServlet", "SELECT Churn Risk HAVING", sqlChurn, churn.length());
            }
            response.put("churn_risk_users", churn);

            // Top content with RANK() window function
            String sqlRank =
                    "SELECT ci.title, ci.type, " +
                            "ROUND(AVG(rr.rating), 2) AS avg_rating, " +
                            "COUNT(rr.review_id) AS review_count, " +
                            "RANK() OVER (ORDER BY AVG(rr.rating) DESC) AS ranking " +
                            "FROM Reviews_Ratings rr " +
                            "JOIN Content_Items ci ON rr.content_id = ci.content_id " +
                            "GROUP BY ci.content_id, ci.title, ci.type " +
                            "ORDER BY ranking LIMIT 10";
            JSONArray ranked = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlRank)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject r = new JSONObject();
                    r.put("title",        rs.getString("title"));
                    r.put("type",         rs.getString("type"));
                    r.put("avg_rating",   rs.getDouble("avg_rating"));
                    r.put("review_count", rs.getInt("review_count"));
                    r.put("ranking",      rs.getInt("ranking"));
                    ranked.put(r);
                }
                QueryLogger.log("AdminServlet", "SELECT Ranked RANK()", sqlRank, ranked.length());
            }
            response.put("ranked_content", ranked);
            response.put("mongo_top_content", getMongoTopContent());

            out.print(response);

        } catch (SQLException e) {
            resp.setStatus(500);
            out.print(new JSONObject().put("error", e.getMessage()));
        }
    }

    private JSONArray getMongoTopContent() {
        JSONArray result = new JSONArray();
        try (MongoClient mc = MongoClients.create("mongodb://localhost:27017")) {
            MongoCollection<Document> col = mc.getDatabase("streamvault")
                    .getCollection("watch_history");
            for (Document doc : col.aggregate(Arrays.asList(
                    Document.parse("{ $group: { _id: '$content_id', total: { $sum: 1 }, completed: { $sum: { $cond: ['$completed', 1, 0] } } } }"),
                    Document.parse("{ $addFields: { completion_rate: { $multiply: [ { $divide: ['$completed','$total'] }, 100 ] } } }"),
                    Document.parse("{ $sort: { completion_rate: -1 } }"),
                    Document.parse("{ $limit: 10 }")
            ))) {
                JSONObject o = new JSONObject();
                o.put("content_id",      doc.getInteger("_id"));
                o.put("total_views",     doc.getInteger("total"));
                o.put("completed_views", doc.getInteger("completed"));
                o.put("completion_rate", doc.getDouble("completion_rate"));
                result.put(o);
            }
        } catch (Exception e) {
            System.err.println("MongoDB error: " + e.getMessage());
        }
        return result;
    }
}
