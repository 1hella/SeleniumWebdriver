package com.wanhella;

import com.google.common.collect.ImmutableList;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.devtools.v114.dom.model.Rect;
import org.openqa.selenium.devtools.v114.page.Page;
import org.openqa.selenium.devtools.v114.page.model.Viewport;
import org.openqa.selenium.devtools.v114.performance.Performance;
import org.openqa.selenium.devtools.v114.performance.model.Metric;
import org.openqa.selenium.devtools.v85.network.Network;
import org.openqa.selenium.devtools.v85.network.model.BlockedReason;
import org.openqa.selenium.devtools.v85.network.model.ConnectionType;
import org.openqa.selenium.devtools.v85.network.model.Headers;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

import static java.lang.invoke.MethodHandles.lookup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openqa.selenium.devtools.v114.page.Page.getLayoutMetrics;
import static org.slf4j.LoggerFactory.getLogger;

public class ChromeDevtoolsTest {
    static final Logger log = getLogger(lookup().lookupClass());
    private static final String WEB_FORM_URL = "https://bonigarcia.dev/selenium-webdriver-java/web-form.html";
    private static final String WEB_ROOT_URL = "https://bonigarcia.dev/selenium-webdriver-java/";

    private WebDriver driver;

    DevTools devTools;

    @BeforeAll
    static void setupClass() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void setup() {
        driver = WebDriverManager.chromedriver().create();
        devTools = ((ChromeDriver) driver).getDevTools();
        devTools.createSession();
    }

    @AfterEach
    void teardown() {
        devTools.close();
        driver.quit();
    }

    @Test
    void testNetworkInterceptor() throws Exception {
        Path img = Paths.get(ClassLoader.getSystemResource("tools.png").toURI());
        byte[] bytes = Files.readAllBytes(img);

        try (NetworkInterceptor interceptor = new NetworkInterceptor(
                driver,
                Route.matching(req -> req.getUri().endsWith(".png"))
                        .to(() -> req -> new HttpResponse()
                                .setContent(Contents.bytes(bytes))))) {
            driver.get(WEB_ROOT_URL);

            int width = Integer.parseInt(driver.findElement(By.tagName("img")).getAttribute("width"));
            assertThat(width).isGreaterThan(80);
        }
    }

    @Test
    void testBasicAuth() {
        ((HasAuthentication) driver).register(() -> new UsernameAndPassword("guest", "guest"));
        driver.get("https://jigsaw.w3.org/HTTP/Basic/");

        WebElement body = driver.findElement(By.tagName("body"));
        assertThat(body.getText()).contains("Your browser made it!");
    }

    @Test
    void testGenericAuth() {
        driver.get("https://guest:guest@jigsaw.w3.org/HTTP/Basic");

        WebElement body = driver.findElement(By.tagName("body"));
        assertThat(body.getText()).contains("Your browser made it!");
    }

    @Test
    void testEmulateNetworkConditions() {
        devTools.send(Network.enable(Optional.empty(), Optional.empty(),
                Optional.empty()));
        devTools.send(Network.emulateNetworkConditions(false, 100, 50*1024, 50*1024,
                Optional.of(ConnectionType.CELLULAR3G)));

        long initMillis = System.currentTimeMillis();
        driver.get(WEB_ROOT_URL);
        Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - initMillis);
        System.out.println("The page took " + elapsed.toMillis() + " ms to be loaded");

        assertThat(driver.getTitle()).contains("Selenium WebDriver");
    }

    @Test
    void testNetworkMonitoring() {
        devTools.send(Network.enable(Optional.empty(), Optional.empty(),
                Optional.empty()));

        devTools.addListener(Network.requestWillBeSent(), request -> {
            System.out.printf("Request %s%n", request.getRequestId());
            System.out.printf("\t Method: %s%n", request.getRequest().getMethod());
            System.out.printf("\t URL: %s%n", request.getRequest().getUrl());
            logHeaders(request.getRequest().getHeaders());
        });

        devTools.addListener(Network.responseReceived(), response -> {
            System.out.printf("Response %s%n", response.getRequestId());
            System.out.printf("\t URL: %s%n", response.getResponse().getUrl());
            System.out.printf("\t Status: %s%n", response.getResponse().getStatus());
            logHeaders(response.getResponse().getHeaders());
        });

        driver.get(WEB_ROOT_URL);
        assertThat(driver.getTitle()).contains("Selenium WebDriver");
    }

    void logHeaders(Headers headers) {
        System.out.printf("\t Headers:%n");
        headers.toJson().forEach((k, v) -> System.out.printf("\t\t%s:%s%n", k, v));
    }

    @Test
    void testFullPageScreenshotChrome() throws IOException {
        driver.get(WEB_ROOT_URL + "long-page.html");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfNestedElementsLocatedBy(By.className("container"), By.tagName("p")));
        Page.GetLayoutMetricsResponse metrics = devTools.send(getLayoutMetrics());
        Rect contentSize = metrics.getContentSize();
        String screenshotBase64 = devTools.send(Page.captureScreenshot(Optional.empty(), Optional.empty(),
                Optional.of(new Viewport(0, 0, contentSize.getWidth(),
                        contentSize.getHeight(), 1)),
                Optional.empty(), Optional.of(true), Optional.of(true)));
        Path destination = Paths.get("fullpage-screenshot-chrome.png");
        Files.write(destination, Base64.getDecoder().decode(screenshotBase64));

        assertThat(destination).exists();
    }

    @Test
    void testPerformanceMetrics() {
        devTools.send(Performance.enable(Optional.empty()));
        driver.get(WEB_ROOT_URL);

        List<Metric> metrics = devTools.send(Performance.getMetrics());
        assertThat(metrics).isNotEmpty();
        metrics.forEach(metric -> System.out.printf("%s: %s%n", metric.getName(), metric.getValue()));
    }

    @Test
    void testExtraHeaders() {
        devTools.send(Network.enable(Optional.empty(), Optional.empty(),
                Optional.empty()));

        String userName = "guest";
        String password = "guest";
        Map<String, Object> headers = new HashMap<>();
        String basicAuth = "Basic " + new String(Base64.getEncoder()
                .encode(String.format("%s:%s", userName, password).getBytes()));
        headers.put("Authorization", basicAuth);
        devTools.send(Network.setExtraHTTPHeaders(new Headers(headers)));

        driver.get("https://jigsaw.w3.org/HTTP/Basic/");
        String bodyText = driver.findElement(By.tagName("body")).getText();
        assertThat(bodyText).contains("Your browser made it!");
    }

    @Test
    void testBlockUrl() {
        devTools.send(Network.enable(Optional.empty(), Optional.empty(),
                Optional.empty()));

        String urlToBlock = WEB_ROOT_URL + "img/hands-on-icon.png";
        devTools.send(Network.setBlockedURLs(ImmutableList.of(urlToBlock)));

        devTools.addListener(Network.loadingFailed(), loadingFailed -> {
            BlockedReason reason = loadingFailed.getBlockedReason().get();
            System.out.printf("Blocking reason: %s%n", reason);
            assertThat(reason).isEqualTo(BlockedReason.INSPECTOR);
        });

        driver.get(WEB_ROOT_URL);
        assertThat(driver.getTitle()).contains("Selenium WebDriver");
    }

    @Test
    void testDeviceEmulation() {
        // 1. Override user agent (Applie iPhone 6)
        String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 8_0 like Mac OS X)"
                + "AppleWebKit/600.1.3 (KHTML, like Gecko)"
                + "Version/8.0 Mobile/12A4345d Safari/600.1.4";
        devTools.send(Network.setUserAgentOverride(userAgent, Optional.empty(),
                Optional.empty(), Optional.empty()));

        // 2. Emulate device dimension
        Map<String, Object> deviceMetrics = new HashMap<>();
        deviceMetrics.put("width", 375);
        deviceMetrics.put("height", 667);
        deviceMetrics.put("mobile", true);
        deviceMetrics.put("deviceScaleFactor", 2);
        ((ChromeDriver) driver).executeCdpCommand(
                "Emulation.setDeviceMetricsOverride", deviceMetrics);

        driver.get(WEB_ROOT_URL);
        assertThat(driver.getTitle()).contains("Selenium WebDriver");
    }
}
