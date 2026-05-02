package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/review")
public class ReviewServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            resp.setStatus(401);
            out.print(new JSONObject().put("error","Not authenticated"));
            return;
        }
        int userId    = (int) session.getAttribute("user_id");
        int contentId = Integer.parseInt(req.getParameter("content_id"));
        int rating    = Integer.parseInt(req.getParameter("rating"));
        String text   = req.getParameter("review_text");

        if (rating < 1 || rating > 10) {
            resp.setStatus(400);
            out.print(new JSONObject().put("error","Rating must be between 1 and 10."));
            return;
        }

        String sql = "INSERT INTO Reviews_Ratings (user_id, content_id, rating, review_text) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE rating=VALUES(rating), review_text=VALUES(review_text)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, contentId);
            ps.setInt(3, rating);
            ps.setString(4, text);
            ps.executeUpdate();
            out.print(new JSONObject().put("success", true).put("message","Review submitted."));
        } catch (SQLException e) {
            resp.setStatus(500);
            out.print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
