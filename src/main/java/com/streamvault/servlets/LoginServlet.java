package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import com.streamvault.util.AuthService;
import com.streamvault.util.QueryLogger;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        JSONObject result = new JSONObject();

        String email    = req.getParameter("email");
        String password = req.getParameter("password");

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            resp.setStatus(400);
            result.put("success", false);
            result.put("message", "Email and password are required.");
            out.print(result);
            return;
        }

        String sql = "SELECT user_id, full_name, password_hash, role, is_active " +
                "FROM Users WHERE email = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            QueryLogger.log("LoginServlet", "SELECT", sql);

            if (rs.next()) {
                boolean isActive   = rs.getBoolean("is_active");
                String storedHash  = rs.getString("password_hash");
                String role        = rs.getString("role");

                if (!isActive) {
                    resp.setStatus(403);
                    result.put("success", false);
                    result.put("message", "Account is inactive.");
                } else if (AuthService.verifyPassword(password, storedHash)) {
                    HttpSession session = req.getSession(true);
                    session.setAttribute("user_id",   rs.getInt("user_id"));
                    session.setAttribute("full_name", rs.getString("full_name"));
                    session.setAttribute("role",      role);

                    result.put("success",  true);
                    result.put("role",     role);
                    result.put("message",  "Login successful.");
                } else {
                    resp.setStatus(401);
                    result.put("success", false);
                    result.put("message", "Invalid email or password.");
                }
            } else {
                resp.setStatus(401);
                result.put("success", false);
                result.put("message", "Invalid email or password.");
            }

        } catch (SQLException e) {
            resp.setStatus(500);
            result.put("success", false);
            result.put("message", "Server error: " + e.getMessage());
        }

        out.print(result);
    }
}
