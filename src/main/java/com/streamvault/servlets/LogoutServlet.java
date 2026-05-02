package com.streamvault.servlets;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;

import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        resp.setContentType("application/json");
        resp.getWriter().print(new JSONObject()
                .put("success", true)
                .put("message", "Logged out successfully."));
        
    }


}