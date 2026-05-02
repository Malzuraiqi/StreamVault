package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/watchlist")
public class WatchlistServlet extends HttpServlet {

    // GET — fetch user's watchlist
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            resp.setStatus(401); out.print(new JSONObject().put("error","Not authenticated")); return;
        }
        int userId = (int) session.getAttribute("user_id");
        String sql =
                "SELECT ci.content_id, ci.title, ci.type, ci.release_year, w.added_time " +
                        "FROM Watchlist w JOIN Content_Items ci ON w.content_id = ci.content_id " +
                        "WHERE w.user_id = ? ORDER BY w.added_time DESC";
        JSONArray arr = new JSONArray();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("content_id",   rs.getInt("content_id"));
                item.put("title",        rs.getString("title"));
                item.put("type",         rs.getString("type"));
                item.put("release_year", rs.getInt("release_year"));
                item.put("added_time",   rs.getString("added_time"));
                arr.put(item);
            }
            out.print(arr);
        } catch (SQLException e) {
            resp.setStatus(500); out.print(new JSONObject().put("error", e.getMessage()));
        }
    }

    // POST — add or remove from watchlist
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            resp.setStatus(401); out.print(new JSONObject().put("error","Not authenticated")); return;
        }
        int userId = (int) session.getAttribute("user_id");
        int contentId = Integer.parseInt(req.getParameter("content_id"));
        String action = req.getParameter("action"); // "add" or "remove"

        try (Connection conn = DatabaseConnection.getConnection()) {
            if ("add".equals(action)) {
                String sql = "INSERT IGNORE INTO Watchlist (user_id, content_id, added_time) VALUES (?,?,NOW())";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId); ps.setInt(2, contentId);
                    ps.executeUpdate();
                    out.print(new JSONObject().put("success", true).put("message","Added to watchlist."));
                }
            } else if ("remove".equals(action)) {
                String sql = "DELETE FROM Watchlist WHERE user_id = ? AND content_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId); ps.setInt(2, contentId);
                    ps.executeUpdate();
                    out.print(new JSONObject().put("success", true).put("message","Removed from watchlist."));
                }
            }
        } catch (SQLException e) {
            resp.setStatus(500); out.print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
