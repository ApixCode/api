package com.example.bypass.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class BypassController {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String STATIC_BASE = "https://publisher.linkvertise.com/api/v1/redirect/link/static/";
    private static final String TARGET_BASE = "https://publisher.linkvertise.com/api/v1/redirect/link/";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @GetMapping("/api/bypass")
    public ResponseEntity<?> bypass(@RequestParam("url") String url) {
        try {
            String path = extractPath(url);
            if (path == null) {
                return ResponseEntity.badRequest().body("Invalid Linkvertise URL");
            }

            String result = tryApiBypass(path);
            if (result != null && !result.isEmpty()) {
                return ResponseEntity.ok(new BypassResponse(url, result));
            }

            result = tryHeadlessBypass(url);
            if (result != null && !result.isEmpty()) {
                return ResponseEntity.ok(new BypassResponse(url, result));
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Bypass failed: Link may require advanced protection bypass or be invalid.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error during bypass: " + e.getMessage());
        }
    }

    private String tryApiBypass(String path) throws Exception {
        var reqBuilder = HttpRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://linkvertise.com/")
                .header("Accept", "application/json");

        String staticUrl = STATIC_BASE + path;
        var staticReq = reqBuilder.uri(URI.create(staticUrl)).GET().build();
        var staticResp = httpClient.send(staticReq, HttpResponse.BodyHandlers.ofString());

        if (staticResp.statusCode() != 200) return null;

        JsonNode json = objectMapper.readTree(staticResp.body());
        int linkId = json.path("data").path("link").path("id").asInt(-1);
        if (linkId == -1) return null;

        if (json.path("data").path("link").path("protected").asBoolean(false)) return null;

        long ts = Instant.now().toEpochMilli();
        String serialJson = "{\"timestamp\":" + ts + ",\"random\":\"6548307\"}";
        String serial = Base64.getEncoder().encodeToString(serialJson.getBytes());

        String targetUrlStr = TARGET_BASE + path + "/target?serial=" + serial;
        var targetReq = reqBuilder.uri(URI.create(targetUrlStr)).GET().build();
        var targetResp = httpClient.send(targetReq, HttpResponse.BodyHandlers.ofString());

        if (targetResp.statusCode() != 200) return null;

        JsonNode targetJson = objectMapper.readTree(targetResp.body());
        String target = targetJson.path("data").path("target").asText(null);

        if (target == null) {
            JsonNode errors = targetJson.path("errors");
            if (!errors.isEmpty() && (errors.get(0).path("message").asText("").contains("captcha") ||
                                      errors.get(0).path("message").asText("").contains("protected"))) {
                return null;
            }
        }

        return target;
    }

    private String tryHeadlessBypass(String url) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage",
                "--user-agent=" + USER_AGENT);
        // For stealth (optional, add if detection issues)
        // options.addArguments("--disable-blink-features=AutomationControlled");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);
            new WebDriverWait(driver, Duration.ofSeconds(45))
                    .until(d -> !d.getCurrentUrl().contains("linkvertise.com") &&
                            !d.getCurrentUrl().contains("publisher.linkvertise.com"));
            return driver.getCurrentUrl();
        } catch (Exception e) {
            System.err.println("Headless bypass failed: " + e.getMessage());
            return null;
        } finally {
            driver.quit();
        }
    }

    private String extractPath(String url) {
        Pattern p = Pattern.compile("linkvertise\\.com/([^?]+)");
        Matcher m = p.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static class BypassResponse {
        public String original;
        public String bypassed;

        public BypassResponse(String original, String bypassed) {
            this.original = original;
            this.bypassed = bypassed;
        }
    }
                               }
