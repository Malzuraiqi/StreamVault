package com.streamvault;

import com.streamvault.db.DatabaseConnection;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class AppLifecycleListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[StreamVault] Application started.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        DatabaseConnection.closeDataSource();
        System.out.println("[StreamVault] Application stopped. Pool closed.");
    }
}