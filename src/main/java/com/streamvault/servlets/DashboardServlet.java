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

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            resp.setStatus(401);
            resp.getWriter().print(new JSONObject().put("error","Not authenticated"));
            return;
        }
        int userId = (int) session.getAttribute("user_id");
        String action = req.getParameter("action");

        if ("toggle_autorenew".equals(action)) {
            String sql = "UPDATE Subscriptions SET auto_renew = NOT auto_renew " +
                    "WHERE user_id = ? AND status = 'active'";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.executeUpdate();
                resp.getWriter().print(new JSONObject().put("success", true));
            } catch (SQLException e) {
                resp.setStatus(500);
                resp.getWriter().print(new JSONObject().put("error", e.getMessage()));
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            resp.setStatus(401);
            out.print(new JSONObject().put("error", "Not authenticated"));
            return;
        }

        int userId = (int) session.getAttribute("user_id");
        JSONObject response = new JSONObject();

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1. Current subscription + plan details
            String sqlPlan =
                    "SELECT sp.plan_name, sp.monthly_price, sp.max_streams, " +
                            "sp.resolution_limit, sp.has_downloads, " +
                            "s.start_date, s.end_date, s.status, s.auto_renew " +
                            "FROM Subscriptions s " +
                            "JOIN Subscription_Plans sp ON s.plan_id = sp.plan_id " +
                            "WHERE s.user_id = ? AND s.status = 'active' LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlPlan)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    JSONObject plan = new JSONObject();
                    plan.put("plan_name",        rs.getString("plan_name"));
                    plan.put("monthly_price",    rs.getDouble("monthly_price"));
                    plan.put("max_streams",      rs.getInt("max_streams"));
                    plan.put("resolution_limit", rs.getString("resolution_limit"));
                    plan.put("has_downloads",    rs.getBoolean("has_downloads"));
                    plan.put("start_date",       rs.getString("start_date"));
                    plan.put("end_date",         rs.getString("end_date"));
                    plan.put("status",           rs.getString("status"));
                    plan.put("auto_renew",       rs.getBoolean("auto_renew"));
                    response.put("subscription", plan);
                } else {
                    response.put("subscription", JSONObject.NULL);
                }
                QueryLogger.log("DashboardServlet", "SELECT Subscription", sqlPlan);
            }

            // 2. Billing history
            String sqlPayments =
                    "SELECT p.amount, p.currency, p.method, p.payment_date, p.status, " +
                            "sp.plan_name " +
                            "FROM Payments p " +
                            "JOIN Subscriptions s ON p.subscription_id = s.subscription_id " +
                            "JOIN Subscription_Plans sp ON s.plan_id = sp.plan_id " +
                            "WHERE s.user_id = ? ORDER BY p.payment_date DESC LIMIT 20";
            JSONArray payments = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlPayments)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject p = new JSONObject();
                    p.put("amount",       rs.getDouble("amount"));
                    p.put("currency",     rs.getString("currency"));
                    p.put("method",       rs.getString("method"));
                    p.put("payment_date", rs.getString("payment_date"));
                    p.put("status",       rs.getString("status"));
                    p.put("plan_name",    rs.getString("plan_name"));
                    payments.put(p);
                }
                QueryLogger.log("DashboardServlet", "SELECT Billing", sqlPayments, payments.length());
            }
            response.put("billing_history", payments);

            // 3. Continue watching (incomplete)
            String sqlContinue =
                    "SELECT ci.content_id, ci.title, ci.type, wh.progress_pct, " +
                            "wh.device_type, wh.watch_date " +
                            "FROM Watch_History wh " +
                            "JOIN Content_Items ci ON wh.content_id = ci.content_id " +
                            "WHERE wh.user_id = ? AND wh.completed = 0 AND wh.progress_pct < 100 " +
                            "ORDER BY wh.watch_date DESC LIMIT 10";
            JSONArray continueWatching = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlContinue)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject w = new JSONObject();
                    w.put("content_id",   rs.getInt("content_id"));
                    w.put("title",        rs.getString("title"));
                    w.put("type",         rs.getString("type"));
                    w.put("progress_pct", rs.getDouble("progress_pct"));
                    w.put("device_type",  rs.getString("device_type"));
                    w.put("watch_date",   rs.getString("watch_date"));
                    continueWatching.put(w);
                }
            }
            response.put("continue_watching", continueWatching);

            // 4. Last 30 days watch history
            String sqlHistory =
                    "SELECT ci.content_id, ci.title, ci.type, wh.progress_pct, " +
                            "wh.completed, wh.device_type, wh.watch_date " +
                            "FROM Watch_History wh " +
                            "JOIN Content_Items ci ON wh.content_id = ci.content_id " +
                            "WHERE wh.user_id = ? " +
                            "AND wh.watch_date >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                            "ORDER BY wh.watch_date DESC";
            JSONArray history = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlHistory)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject h = new JSONObject();
                    h.put("content_id",   rs.getInt("content_id"));
                    h.put("title",        rs.getString("title"));
                    h.put("type",         rs.getString("type"));
                    h.put("progress_pct", rs.getDouble("progress_pct"));
                    h.put("completed",    rs.getBoolean("completed"));
                    h.put("device_type",  rs.getString("device_type"));
                    h.put("watch_date",   rs.getString("watch_date"));
                    history.put(h);
                }
                QueryLogger.log("DashboardServlet", "SELECT Watch History", sqlHistory, history.length());
            }
            response.put("watch_history", history);

            out.print(response);

        } catch (SQLException e) {
            resp.setStatus(500);
            out.print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
