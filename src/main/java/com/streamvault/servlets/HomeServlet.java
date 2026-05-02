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

@WebServlet("/home")
public class HomeServlet extends HttpServlet {

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

        String genre    = req.getParameter("genre");
        String type     = req.getParameter("type");
        String language = req.getParameter("language");
        String search   = req.getParameter("search");

        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT ci.content_id, ci.title, ci.type, ci.release_year, " +
                        "ci.language, ci.age_rating, ci.duration_minutes, " +
                        "COALESCE(AVG(rr.rating), 0) AS avg_rating " +
                        "FROM Content_Items ci " +
                        "LEFT JOIN Reviews_Ratings rr ON ci.content_id = rr.content_id " +
                        "LEFT JOIN Content_Genre cg ON ci.content_id = cg.content_id " +
                        "LEFT JOIN Genres g ON cg.genre_id = g.genre_id " +
                        "WHERE 1=1 "
        );

        if (genre != null && !genre.isBlank())
            sql.append("AND g.name = ? ");
        if (type != null && !type.isBlank())
            sql.append("AND ci.type = ? ");
        if (language != null && !language.isBlank())
            sql.append("AND ci.language = ? ");
        if (search != null && !search.isBlank())
            sql.append("AND ci.title LIKE ? ");

        sql.append("GROUP BY ci.content_id ORDER BY avg_rating DESC LIMIT 50");

        String query = sql.toString();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            int idx = 1;
            if (genre    != null && !genre.isBlank())    ps.setString(idx++, genre);
            if (type     != null && !type.isBlank())     ps.setString(idx++, type);
            if (language != null && !language.isBlank()) ps.setString(idx++, language);
            if (search   != null && !search.isBlank())   ps.setString(idx++, "%" + search + "%");

            ResultSet rs = ps.executeQuery();
            JSONArray arr = new JSONArray();

            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("content_id",       rs.getInt("content_id"));
                item.put("title",            rs.getString("title"));
                item.put("type",             rs.getString("type"));
                item.put("release_year",     rs.getInt("release_year"));
                item.put("language",         rs.getString("language"));
                item.put("age_rating",       rs.getString("age_rating"));
                item.put("duration_minutes", rs.getObject("duration_minutes"));
                item.put("avg_rating",       Math.round(rs.getDouble("avg_rating") * 10.0) / 10.0);
                arr.put(item);
            }
            QueryLogger.log("HomeServlet", "SELECT", sql.toString(), arr.length());
            out.print(arr);

        } catch (SQLException e) {
            resp.setStatus(500);
            out.print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
