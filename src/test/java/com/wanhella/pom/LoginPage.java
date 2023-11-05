package com.wanhella.pom;

import org.openqa.selenium.By;

public class LoginPage extends BasePage {
    By usernameInput = By.id("username");
    By passwordInput = By.id("password");
    By submitButton = By.cssSelector("button");
    By successBox = By.id("success");


}
