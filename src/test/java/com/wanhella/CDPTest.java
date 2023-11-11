package com.wanhella;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.events.CdpEventTypes;
import org.openqa.selenium.devtools.events.ConsoleEvent;
import org.openqa.selenium.devtools.events.DomMutationEvent;
import org.openqa.selenium.devtools.v114.network.model.Cookie;
import org.openqa.selenium.devtools.v114.network.Network;
import org.openqa.selenium.devtools.v114.emulation.Emulation;
import org.openqa.selenium.devtools.v114.security.Security;
import org.openqa.selenium.html5.Location;
import org.openqa.selenium.html5.LocationContext;
import org.openqa.selenium.logging.HasLogEvents;
import org.openqa.selenium.print.PrintOptions;
import org.openqa.selenium.support.Color;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.virtualauthenticator.HasVirtualAuthenticator;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticator;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticatorOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class CDPTest {
    private static final String WEB_ROOT = "https://bonigarcia.dev/selenium-webdriver-java/";

    WebDriver driver;
    DevTools devTools;

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
    void testConsoleListener() throws Exception {
        CompletableFuture<ConsoleEvent> futureEvents = new CompletableFuture<>();
        devTools.getDomains().events()
                .addConsoleListener(futureEvents::complete);

        CompletableFuture<JavascriptException> futureJsExc = new CompletableFuture<>();
        devTools.getDomains().events()
                .addJavascriptExceptionListener(futureJsExc::complete);

        driver.get(WEB_ROOT + "console-logs.html");

        ConsoleEvent consoleEvent = futureEvents.get(5, TimeUnit.SECONDS);
        System.out.printf("ConsoleEvent: %s %s %s%n", consoleEvent.getTimestamp(),
                consoleEvent.getType(), consoleEvent.getMessages());

        JavascriptException jsException = futureJsExc.get(5, TimeUnit.SECONDS);
        System.out.printf("JavascriptException: %s %s%n", jsException.getMessage(), jsException.getSystemInformation());
    }

    @Test
    void testGeolocationOverride() {
        devTools.send(Emulation.setGeolocationOverride(Optional.of(48.8584), Optional.of(2.2945),
                Optional.of(100)));

        driver.get(WEB_ROOT + "geolocation.html");
        driver.findElement(By.id("get-coordinates")).click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        WebElement coordinates = driver.findElement(By.id("coordinates"));
        wait.until(ExpectedConditions.visibilityOf((coordinates)));
    }

    @Test
    void testManageCookies() {
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        driver.get(WEB_ROOT + "cookies.html");

        // Read cookies
        List<Cookie> cookies = devTools.send(Network.getAllCookies());
        cookies.forEach(cookie -> System.out.printf("%s=%s", cookie.getName(), cookie.getValue()));
        List<String> cookieName = cookies.stream()
                .map(Cookie::getName).sorted()
                .toList();
        Set<org.openqa.selenium.Cookie> seleniumCookie = driver.manage().getCookies();
        List<String> selCookieName = seleniumCookie.stream()
                .map(org.openqa.selenium.Cookie::getName).sorted()
                .toList();
        assertThat(cookieName).isEqualTo(selCookieName);

        // Clear cookies
        devTools.send(Network.clearBrowserCookies());
        List<Cookie> cookiesAfterClearing = devTools.send(Network.getAllCookies());
        assertThat(cookiesAfterClearing).isEmpty();

        driver.findElement(By.id("refresh-cookies")).click();
    }

    @Test
    void testLoadInsecure() {
        devTools.send(Security.enable());
        devTools.send(Security.setIgnoreCertificateErrors(true));
        driver.get("https://expired.badssl.com/");

        String bgColor = driver.findElement(By.tagName("body"))
                .getCssValue("background-color");
        Color red = new Color(255, 0, 0, 1);
        assertThat(Color.fromString(bgColor)).isEqualTo(red);
    }

    @Test
    void testLocationContext() {
        LocationContext location = (LocationContext) driver;
        location.setLocation(new Location(27.5916, 86.5640, 8850));

        driver.get(WEB_ROOT + "geolocation.html");
        driver.findElement(By.id("get-coordinates")).click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        WebElement coordinates = driver.findElement(By.id("coordinates"));
        wait.until(ExpectedConditions.visibilityOf(coordinates));
    }

    @Test
    void testWebAuthn() {
        driver.get("https://webauthn.io/");
        HasVirtualAuthenticator virtualAuth = (HasVirtualAuthenticator) driver;
        VirtualAuthenticator authenticator = virtualAuth
                .addVirtualAuthenticator(new VirtualAuthenticatorOptions());

        String randomId = UUID.randomUUID().toString();
        driver.findElement(By.id("input-email")).sendKeys(randomId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        driver.findElement(By.id("register-button")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.className("alert-success"),
                "Success! Now try to authenticate..."));

        driver.findElement(By.id("login-button")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.className("main-content"),
                "You're logged in!"));

        virtualAuth.removeVirtualAuthenticator(authenticator);
    }

    @Test
    void testPrint() throws IOException {
        driver.get(WEB_ROOT);
        PrintsPage pg = (PrintsPage) driver;
        PrintOptions printOptions = new PrintOptions();
        Pdf pdf = pg.print(printOptions);

        String pdfBase64 = pdf.getContent();
        assertThat(pdfBase64).contains("JVBER");

        byte[] decodedImg = Base64.getDecoder()
                .decode(pdfBase64.getBytes(StandardCharsets.UTF_8));
        Path destinationFile = Paths.get("my-pdf.pdf");
        Files.write(destinationFile, decodedImg);
    }

    @Test
    void testDomMutation() throws InterruptedException {
        driver.get(WEB_ROOT);

        HasLogEvents logger = (HasLogEvents) driver;
        JavascriptExecutor js = (JavascriptExecutor) driver;

        AtomicReference<DomMutationEvent> seen = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        logger.onLogEvent(CdpEventTypes.domMutation(mutation -> {
            seen.set(mutation);
            latch.countDown();
        }));

        WebElement img = driver.findElement(By.tagName("img"));
        String newSrc = "img/award.png";
        String script = String.format("arguments[0].src = '%s'", newSrc);
        js.executeScript(script, img);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(seen.get().getElement().getAttribute("src")).endsWith(newSrc);
    }

    @Test
    void testConsoleEvents() throws InterruptedException {
        HasLogEvents logger = (HasLogEvents) driver;

        CountDownLatch latch = new CountDownLatch(4);
        logger.onLogEvent(CdpEventTypes.consoleEvent(consoleEvent -> {
            System.out.printf("%s %s: %s%n", consoleEvent.getTimestamp(),
                    consoleEvent.getType(), consoleEvent.getMessages());
            latch.countDown();
        }));

        driver.get(WEB_ROOT + "console-logs.html");

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }
}
