package com.wanhella.pageobjectmodel;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class ExtendedBasePage {
    WebDriver driver;
    WebDriverWait wait;
    int timeoutSec = 5;

    public ExtendedBasePage(String browser) {
        driver = WebDriverManager.getInstance(browser).create();
        wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSec));
    }

    public void setTimeoutSec(int timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
        }
    }

    public void visit(String url) {
        driver.get(url);
    }

    public WebElement find(By element) {
        return driver.findElement(element);
    }

    public void click(By element) {
        find(element).click();
    }

    public void click(WebElement element) {
        element.click();
    }

    public void type(By element, String text) {
        find(element).sendKeys(text);
    }

    public void type(WebElement element, String text) {
        element.sendKeys(text);
    }

    public boolean isDisplayed(By locator) {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            System.out.printf("Timeout of %s wait for %s%n", timeoutSec, locator);
            return false;
        }
        return true;
    }

    public boolean isDisplayed(WebElement element) {
        try {
            wait.until(ExpectedConditions.visibilityOf(element));
        } catch (TimeoutException e) {
            System.out.printf("Timeout of %s wait for %s%n", timeoutSec, element);
            return false;
        }
        return true;
    }
}
