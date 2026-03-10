package com.noelledotjpg.TabContent;

import com.noelledotjpg.Data.PreferencesData;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;

public class UpdateNotesTab extends JPanel {

    private WebEngine webEngine;

    public UpdateNotesTab(PreferencesData prefs) {
        setLayout(new BorderLayout());

        JFXPanel fxPanel = new JFXPanel();
        add(fxPanel, BorderLayout.CENTER);

        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();

            ScrollPane scrollPane = new ScrollPane(webView);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);

            String styleCss = """
                    body {
                        font-family: sans-serif;
                        background-color: #222222;
                        color: #e0d0d0;
                        padding-left: 16px;
                        padding-right: 16px;
                        overflow: hidden;
                    }
                    ::-webkit-scrollbar {
                        width: 0px;
                        height: 0px;
                    }
                    a { color: #aaaaff; }
                    hr { border:0; color:#111111; background-color:#111111; height:2px; margin-top:16px; }
                    h3 { color:#ffffff; font-size:16px; }
                    img { border:0; margin:0; }
                    .sidebar { padding:10px; vertical-align:top; }
                    .alert { background-color:#aa0000; color:#ffffff; font-weight:bold; padding:6px 10px; width:500px; }
                    """;

            String loadingHtml = "<html>" +
                    "<head><style>" + styleCss + "</style></head>" +
                    "<body style=\"text-align:center; margin-top:50px;\">" +
                    "<h2>Loading...</h2>" +
                    "<p>Please wait while the content loads.</p>" +
                    "</body></html>";

            webEngine.loadContent(loadingHtml);

            new Thread(() -> {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> webEngine.load(prefs.getResolvedNewsUrl()));
            }).start();

            webEngine.getLoadWorker().exceptionProperty().addListener((obs, oldExc, newExc) -> {
                if (newExc != null) {
                    String failedHtml = "<html>" +
                            "<head><style>" + styleCss + "</style></head>" +
                            "<body style=\"text-align:center; margin-top:50px;\">" +
                            "<h2>Failed to load page.</h2>" +
                            "<p>Check your internet connection and try again.</p>" +
                            "</body></html>";
                    Platform.runLater(() -> webEngine.loadContent(failedHtml));
                }
            });

            fxPanel.setScene(new Scene(scrollPane));
        });
    }

    public void reload(String url) {
        Platform.runLater(() -> {
            if (webEngine != null) webEngine.load(url);
        });
    }
}