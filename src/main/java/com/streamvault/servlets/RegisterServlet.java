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

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        JSONObject result = new JSONObject();

        String fullName  = req.getParameter("full_name");
        String email     = req.getParameter("email");
        String password  = req.getParameter("password");
        String dob       = req.getParameter("date_of_birth");
        String country   = req.getParameter("country");
        String planIdStr = req.getParameter("plan_id");

        if (fullName == null || email == null || password == null ||
                dob == null || country == null || planIdStr == null) {
            resp.setStatus(400);
            result.put("success", false);
            result.put("message", "All fields are required.");
            out.print(result);
            return;
        }

        if (password.length() < 8) {
            resp.setStatus(400);
            result.put("success", false);
            result.put("message", "Password must be at least 8 characters.");
            out.print(result);
            return;
        }

        String passwordHash = AuthService.hashPassword(password);
        int planId = Integer.parseInt(planIdStr);

        String insertUser = "INSERT INTO Users (full_name, email, password_hash, date_of_birth, country, role) " +
                "VALUES (?, ?, ?, ?, ?, 'viewer')";
        String insertSub  = "INSERT INTO Subscriptions (user_id, plan_id, start_date, status, auto_renew) " +
                "VALUES (?, ?, CURDATE(), 'active', 1)";
        String insertPayment =
                "INSERT INTO Payments (subscription_id, amount, currency, method, payment_date, status) " +
                        "VALUES (?, (SELECT monthly_price FROM Subscription_Plans WHERE plan_id = ?), 'USD', 'Credit Card', NOW(), 'completed')";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psUser = conn.prepareStatement(insertUser,
                    Statement.RETURN_GENERATED_KEYS)) {
                psUser.setString(1, fullName);
                psUser.setString(2, email);
                psUser.setString(3, passwordHash);
                psUser.setString(4, dob);
                psUser.setString(5, country);
                psUser.executeUpdate();
                QueryLogger.log("RegisterServlet", "INSERT Users", insertUser);

                ResultSet keys = psUser.getGeneratedKeys();
                if (keys.next()) {
                    int newUserId = keys.getInt(1);

                    try (PreparedStatement psSub = conn.prepareStatement(insertSub,
                            Statement.RETURN_GENERATED_KEYS)) {
                        psSub.setInt(1, newUserId);
                        psSub.setInt(2, planId);
                        psSub.executeUpdate();
                        QueryLogger.log("RegisterServlet", "INSERT Subscriptions", insertSub);

                        ResultSet subKeys = psSub.getGeneratedKeys();
                        if (subKeys.next()) {
                            int newSubId = subKeys.getInt(1);
                            try (PreparedStatement psPay = conn.prepareStatement(insertPayment)) {
                                psPay.setInt(1, newSubId);
                                psPay.setInt(2, planId);
                                psPay.executeUpdate();
                                QueryLogger.log("RegisterServlet", "INSERT Payments", insertPayment);
                            }
                        }
                    }
                }

                conn.commit();
                result.put("success", true);
                result.put("message", "Registration successful. Please log in.");

            } catch (SQLException e) {
                conn.rollback();
                if (e.getMessage().contains("Duplicate entry")) {
                    resp.setStatus(409);
                    result.put("success", false);
                    result.put("message", "An account with this email already exists.");
                } else {
                    resp.setStatus(500);
                    result.put("success", false);
                    result.put("message", "Registration failed: " + e.getMessage());
                }
            }

        } catch (SQLException e) {
            resp.setStatus(500);
            result.put("success", false);
            result.put("message", "Server error: " + e.getMessage());
        }

        out.print(result);
    }
}
