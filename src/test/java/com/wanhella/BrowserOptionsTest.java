package com.wanhella;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.support.Color;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;

import static org.assertj.core.api.Assertions.assertThat;

public class BrowserOptionsTest {
    public static final String WEB_ROOT = "https://bonigarcia.dev/selenium-webdriver-java/";
    WebDriver driver;
    String lang;

    @BeforeAll
    static void setupClass() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void setup() {
        lang = "es-ES";
        LoggingPreferences logs = new LoggingPreferences();
        logs.enable(LogType.BROWSER, Level.ALL);

        ChromeOptions options = new ChromeOptions();
        Map<String, Object> mobileEmulation = new HashMap<>();
        mobileEmulation.put("deviceName", "iPhone 6/7/8");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.geolocation", 1);
        prefs.put("profile.default_content_setting_values.notifications", 1);
        prefs.put("intl.accept_languages", lang);

        options.setCapability("goog:loggingPrefs", logs);
        options.setExperimentalOption("mobileEmulation", mobileEmulation);
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--use-fake-ui-for-media-stream");
        options.addArguments("--use-fake-device-for-media-stream");
        options.setAcceptInsecureCerts(true);
//        options.addArguments("--incognito");

        driver = WebDriverManager.chromedriver().capabilities(options).create();
    }

    @AfterEach
    void teardown() {
        driver.quit();
    }

    @Test
    void testGeoLocation() {
        driver.get(WEB_ROOT + "geolocation.html");
        driver.findElement(By.id("get-coordinates")).click();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        WebElement coordinates = wait.until(ExpectedConditions.visibilityOf(driver.findElement(By.id("coordinates"))));
        assertThat(coordinates.isDisplayed()).isTrue();
    }

    @Test
    void testNotification() {
        driver.get(WEB_ROOT + "notifications.html");
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String script = String.join("\n",
                "const callback = arguments[arguments.length - 1];",
                "const OldNotify = window.Notification;",
                "function newNotification(title, options) {",
                "   callback(title);",
                "   return new OldNotify(title, options);",
                "}",
                "newNotification.requestPermission = OldNotify.requestPermission.bind(OldNotify);",
                "Object.defineProperty(newNotification, 'permission', {",
                "   get: function() {",
                "       return OldNotify.permission;",
                "   }",
                "});",
                "window.Notification = newNotification;",
                "document.getElementById('notify-me').click();");
        System.out.println("Executing the following script asynchronously:\n" + script);

        Object notificationTitle = js.executeAsyncScript(script);
        assertThat(notificationTitle).isEqualTo("This is a notification");
    }

    @Test
    void testBrowserLogs() {
        driver.get(WEB_ROOT + "console-logs.html");
        LogEntries browserLogs = driver.manage().logs().get(LogType.BROWSER);
        assertThat(browserLogs.getAll()).isNotEmpty();
        browserLogs.forEach(System.out::println);
    }

    @Test
    void testInsecure() {
        driver.get("https://self-signed.badssl.com/");

        String bgColor = driver.findElement(By.tagName("body")).getCssValue("background-color");
        Color red = new Color(255, 0, 0, 1);
        assertThat(Color.fromString(bgColor)).isEqualTo(red);
    }

    @Test
    void testAcceptLang() {
        driver.get(WEB_ROOT + "multilanguage.html");
        ResourceBundle strings = ResourceBundle.getBundle("strings", Locale.forLanguageTag(lang));
        String home = strings.getString("home");
        String content = strings.getString("content");
        String about = strings.getString("about");
        String contact = strings.getString("contact");

        String bodyText = driver.findElement(By.tagName("body")).getText();
        assertThat(bodyText).contains(home).contains(content).contains(about).contains(contact);
    }
}
