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

@WebServlet("/content")
public class ContentDetailServlet extends HttpServlet {

    // GET — fetch content details, episodes, reviews
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

        String contentIdStr = req.getParameter("id");
        if (contentIdStr == null) {
            resp.setStatus(400);
            out.print(new JSONObject().put("error", "Content ID is required"));
            return;
        }

        int contentId = Integer.parseInt(contentIdStr);
        JSONObject response = new JSONObject();

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1. Main content info + avg rating
            String sqlContent =
                    "SELECT ci.*, s.name AS studio_name, " +
                            "COALESCE(AVG(rr.rating), 0) AS avg_rating, " +
                            "COUNT(rr.review_id) AS review_count " +
                            "FROM Content_Items ci " +
                            "LEFT JOIN Studios s ON ci.studio_id = s.studio_id " +
                            "LEFT JOIN Reviews_Ratings rr ON ci.content_id = rr.content_id " +
                            "WHERE ci.content_id = ? GROUP BY ci.content_id";

            try (PreparedStatement ps = conn.prepareStatement(sqlContent)) {
                ps.setInt(1, contentId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    response.put("content_id",       rs.getInt("content_id"));
                    response.put("title",            rs.getString("title"));
                    response.put("type",             rs.getString("type"));
                    response.put("release_year",     rs.getInt("release_year"));
                    response.put("duration_minutes", rs.getObject("duration_minutes"));
                    response.put("language",         rs.getString("language"));
                    response.put("age_rating",       rs.getString("age_rating"));
                    response.put("synopsis",         rs.getString("synopsis"));
                    response.put("studio_name",      rs.getString("studio_name"));
                    response.put("avg_rating",       Math.round(rs.getDouble("avg_rating") * 10.0) / 10.0);
                    response.put("review_count",     rs.getInt("review_count"));
                }
                QueryLogger.log("ContentDetailServlet", "SELECT Content+Rating", sqlContent);
            }

            // 2. Genres
            String sqlGenres =
                            "SELECT g.name FROM Genres g " +
                            "JOIN Content_Genre cg ON g.genre_id = cg.genre_id " +
                            "WHERE cg.content_id = ?";
            JSONArray genres = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlGenres)) {
                ps.setInt(1, contentId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) genres.put(rs.getString("name"));
                QueryLogger.log("ContentDetailServlet", "SELECT Genres", sqlGenres);
            }
            response.put("genres", genres);

            // 3. Episodes (only if Series)
            String sqlEpisodes =
                    "SELECT episode_id, season_no, episode_no, title, duration_minutes " +
                            "FROM Episodes WHERE series_id = ? ORDER BY season_no, episode_no";
            JSONArray episodes = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlEpisodes)) {
                ps.setInt(1, contentId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject ep = new JSONObject();
                    ep.put("episode_id",       rs.getInt("episode_id"));
                    ep.put("season_no",        rs.getInt("season_no"));
                    ep.put("episode_no",       rs.getInt("episode_no"));
                    ep.put("title",            rs.getString("title"));
                    ep.put("duration_minutes", rs.getInt("duration_minutes"));
                    episodes.put(ep);
                }
                QueryLogger.log("ContentDetailServlet", "SELECT Episodes", sqlEpisodes, episodes.length());
            }
            response.put("episodes", episodes);

            // 4. Reviews
            String sqlReviews =
                    "SELECT u.full_name, rr.rating, rr.review_text " +
                            "FROM Reviews_Ratings rr " +
                            "JOIN Users u ON rr.user_id = u.user_id " +
                            "WHERE rr.content_id = ? ORDER BY rr.review_id DESC";
            JSONArray reviews = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sqlReviews)) {
                ps.setInt(1, contentId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject rv = new JSONObject();
                    rv.put("full_name",    rs.getString("full_name"));
                    rv.put("rating",       rs.getInt("rating"));
                    rv.put("review_text",  rs.getString("review_text"));
                    reviews.put(rv);
                }
                QueryLogger.log("ContentDetailServlet", "SELECT Reviews", sqlReviews, reviews.length());
            }
            response.put("reviews", reviews);

            out.print(response);

        } catch (SQLException e) {
            resp.setStatus(500);
            out.print(new JSONObject().put("error", e.getMessage()));
        }
    }

    // POST — record a play (insert into Watch_History)
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            resp.setStatus(401);
            out.print(new JSONObject().put("error", "Not authenticated"));
            return;
        }

        int userId    = (int) session.getAttribute("user_id");
        String contentIdStr  = req.getParameter("content_id");
        String episodeIdStr  = req.getParameter("episode_id");
        String deviceType    = req.getParameter("device_type");

        if (contentIdStr == null) {
            resp.setStatus(400);
            out.print(new JSONObject().put("error", "Content ID required"));
            return;
        }

        int contentId = Integer.parseInt(contentIdStr);
        Integer episodeId = (episodeIdStr != null && !episodeIdStr.isBlank())
                ? Integer.parseInt(episodeIdStr) : null;
        if (deviceType == null || deviceType.isBlank()) deviceType = "Browser";

        String sql = "INSERT INTO Watch_History " +
                "(user_id, content_id, episode_id, watch_date, progress_pct, device_type, completed) " +
                "VALUES (?, ?, ?, NOW(), 0.00, ?, 0)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, contentId);
            if (episodeId != null) ps.setInt(3, episodeId);
            else ps.setNull(3, Types.INTEGER);
            ps.setString(4, deviceType);
            ps.executeUpdate();
            QueryLogger.log("ContentDetailServlet", "INSERT Watch_History", sql);

            out.print(new JSONObject().put("success", true).put("message", "Playback started."));

        } catch (SQLException e) {
            resp.setStatus(500);
            out.print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
