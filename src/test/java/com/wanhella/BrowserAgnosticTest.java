package com.wanhella;


import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.support.Color;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.lang.invoke.MethodHandles.lookup;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.slf4j.LoggerFactory.getLogger;

public class BrowserAgnosticTest {
    static final Logger log = getLogger(lookup().lookupClass());
    private static final String WEB_FORM_URL = "https://bonigarcia.dev/selenium-webdriver-java/web-form.html";
    private static final String WEB_ROOT_URL = "https://bonigarcia.dev/selenium-webdriver-java/";
    public static final String LONG_PAGE_URL = WEB_ROOT_URL + "long-page.html";

    private WebDriver driver;

    @BeforeAll
    static void setupClass() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void setup() {
        try (ChromeDriverService service = new ChromeDriverService.Builder().withLogOutput(System.out).build()) {
            driver = new ChromeDriver(service);
        }
    }

    @AfterEach
    void teardown() {
        driver.quit();
    }

    @Test
    void testScrollBy() {
        driver.get(LONG_PAGE_URL);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String script = "window.scrollBy(0, 1000);";
        js.executeScript(script);
    }

    @Test
    void testScrollIntoView() {
        driver.get(LONG_PAGE_URL);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        WebElement lastElement = driver.findElement(By.cssSelector("p:last-child"));
        String script = "arguments[0].scrollIntoView();";
        js.executeScript(script, lastElement);
    }

    @Test
    void testInfiniteScroll() {
        driver.get(WEB_ROOT_URL + "infinite-scroll.html");
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        By pLocator = By.tagName("p");
        List<WebElement> paragraphs = wait.until(
                ExpectedConditions.numberOfElementsToBeMoreThan(pLocator, 0));
        int initParagraphsNumber = paragraphs.size();

        WebElement lastParagraph = driver.findElement(By.xpath(String.format("//p[%d]", initParagraphsNumber)));
        String script = "arguments[0].scrollIntoView();";
        js.executeScript(script, lastParagraph);

        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(pLocator, initParagraphsNumber));
    }

    @Test
    void testColorPicker() {
        driver.get(WEB_FORM_URL);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        WebElement colorPicker = driver.findElement(By.name("my-colors"));
        String initColor = colorPicker.getAttribute("value");
        log.debug("The initial color is {}", initColor);

        Color red = new Color(255, 0, 0, 1);
        String script = String.format("arguments[0].setAttribute('value', '%s');", red.asHex());
        js.executeScript(script, colorPicker);

        String finalColor = colorPicker.getAttribute("value");
        log.debug("The final color is {}", finalColor);
        assertThat(finalColor).isNotEqualTo(initColor);
        assertThat(Color.fromString(finalColor)).isEqualTo(red);
    }

    @Test
    void testPinnedScripts() {
        String initPage = WEB_ROOT_URL;
        driver.get(initPage);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        ScriptKey linkKey = js.pin("return document.getElementsByTagName('a')[2];");
        ScriptKey firstArgKey = js.pin("return arguments[0];");

        Set<ScriptKey> pinnedScripts = js.getPinnedScripts();
        assertThat(pinnedScripts).hasSize(2);

        WebElement formLink = (WebElement) js.executeScript(linkKey);
        formLink.click();
        assertThat(driver.getCurrentUrl()).isNotEqualTo(initPage);

        String message = "Hello world!";
        String executeScript = (String) js.executeScript(firstArgKey, message);
        assertThat(executeScript).isEqualTo(message);

        js.unpin(linkKey);
        assertThat(js.getPinnedScripts()).hasSize(1);
    }

    @Test
    void testAsyncScript() {
        driver.get(WEB_ROOT_URL);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        Duration pause = Duration.ofSeconds(2);
        String script = "const callback = arguments[arguments.length - 1];"
                + "window.setTimeout(callback, " + pause.toMillis() + ");";

        long initMillis = System.currentTimeMillis();
        js.executeAsyncScript(script);
        Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - initMillis);

        log.debug("The script took {} ms to be executed", elapsed.toMillis());
        assertThat(elapsed).isGreaterThanOrEqualTo(pause);
    }

    @Test
    void testPageLoadTimeout() {
        driver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(1));

        assertThatThrownBy(() -> driver.get(WEB_ROOT_URL)).isInstanceOf(TimeoutException.class);
    }

    @Test
    void testScriptTimeout() {
        driver.get(WEB_ROOT_URL);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(3));

        assertThatThrownBy(() -> {
            long waitMillis = Duration.ofSeconds(5).toMillis();
            String script = "const callback = arguments[arguments.length - 1];"
                    + "window.setTimeout(callback, " + waitMillis + ");";
            js.executeAsyncScript(script);
        }).isInstanceOf(ScriptTimeoutException.class);
    }

    @Test
    void testScreenshotPng() throws IOException {
        driver.get(WEB_ROOT_URL);
        TakesScreenshot ts = (TakesScreenshot) driver;

        File screenshot = ts.getScreenshotAs(OutputType.FILE);
        log.debug("Screenshot created on {}", screenshot);

        Path destination = Paths.get("screenshot.png");
        Files.move(screenshot.toPath(), destination, REPLACE_EXISTING);
        log.debug("Screenshot moved to {}", destination);

        assertThat(destination).exists();
    }

    @Test
    void testScreenshotBase64() {
        driver.get(WEB_ROOT_URL);
        TakesScreenshot ts = (TakesScreenshot) driver;

        String screenshot = ts.getScreenshotAs(OutputType.BASE64);
        log.debug("Screenshot in base64 \ndata:image/png;base64,{}", screenshot);
        assertThat(screenshot).isNotEmpty();
    }

    @Test
    void testWEbElementScreenshot() throws IOException {
        driver.get(WEB_FORM_URL);

        WebElement form = driver.findElement(By.tagName("form"));
        File screenshot = form.getScreenshotAs(OutputType.FILE);
        Path destination = Paths.get("webelement-screenshot.png");
        Files.move(screenshot.toPath(), destination, REPLACE_EXISTING);
        assertThat(destination).exists();
    }

    @Test
    void testWindow() {
        driver.get(WEB_ROOT_URL);
        WebDriver.Window window = driver.manage().window();

        Point initialPosition = window.getPosition();
        Dimension initialSize = window.getSize();
        log.debug("initial window: position {} -- size {}", initialPosition, initialSize);

        window.maximize();

        Point maximizedPosition = window.getPosition();
        Dimension maximizedSize = window.getSize();
        log.debug("Maximized window: position {} -- size {}", maximizedPosition, maximizedSize);

        assertThat(initialPosition).isNotEqualTo(maximizedPosition);
        assertThat(initialSize).isNotEqualTo(maximizedSize);
    }

    @Test
    void testHistory() {
        String firstPage = WEB_ROOT_URL + "navigation1.html";
        String secondPage = WEB_ROOT_URL + "navigation2.html";
        String thirdPage = WEB_ROOT_URL + "navigation3.html";

        driver.get(firstPage);

        driver.navigate().to(secondPage);
        driver.navigate().to(thirdPage);
        driver.navigate().back();
        driver.navigate().forward();
        driver.navigate().refresh();

        assertThat(driver.getCurrentUrl()).isEqualTo(thirdPage);
    }
}