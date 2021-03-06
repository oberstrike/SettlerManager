package com.main

import io.kesselring.sukejura.pattern.DaysOfWeek
import io.kesselring.sukejura.pattern.Hours
import io.kesselring.sukejura.pattern.Minutes
import io.kesselring.sukejura.sukejura
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


fun log(message: String) {
    val now = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
    )
    println("$now: $message")
}

class Sniffer {
    private val isWindows = System.getProperty("os.name").contains("Windows")
    private val path = if (isWindows) "C:\\Users\\oberstrike\\Desktop\\Depot" else "/data/csv"

    private var databaseReader: DatabaseReader

    private val ingDibaUrl = "https://www.ing.de/"

    init {
        log("Path: ${path + File.separator + "BankingDatabase.kdbx"}")
        log("Linux: ${!isWindows}")

        val files = File(path).list() ?: throw NullPointerException()
        log(files.contentDeepToString())

        databaseReader = DatabaseReader(
                File(path + File.separator + "banking.csv"),
                System.getenv("DATABASEPASSWORD")
        )
        log("Database loaded")

    }


    fun start() {
        val (kontonummer, pin, zugangsnummer, key) = databaseReader.getCredentials()

        try {
            val script = getScript(key)

            val driver = ChromeDriver(getOptions())
            val waitDriver = WebDriverWait(driver, 15)

            driver.get(ingDibaUrl)
            waitDriver.until(ExpectedConditions.visibilityOfElementLocated(By.id("uc-btn-accept-banner")))

            val acceptButton = driver.findElementById("uc-btn-accept-banner")
            acceptButton.click()
            log("Accept-Button clicked.")

            val loginBankingButton = driver.findElementByClassName("button-session")
            loginBankingButton.click()
            log("Login-Button clicked.")

            val zugangsnummerElement = driver.findElementByName("view:zugangskennung:zugangskennung")
            val pinElement = driver.findElementByName("view:pin:pin")
            val btnNext = driver.findElementsByName("buttons:next").first { it.isDisplayed }
            Thread.sleep(5000)

            log("Im sending keys for 'Zugangsnummer'.")
            zugangsnummerElement.sendKeys(zugangsnummer)
            Thread.sleep(1000)

            log("Im sending keys for 'PIN'.")
            pinElement.sendKeys(pin)
            Thread.sleep(1000)

            btnNext.click()
            log("Next-Button clicked.")
            Thread.sleep(5000)

            val js = driver as JavascriptExecutor

            js.executeScript(script)
            Thread.sleep(1000)
            log("Javascript executed.")
            log("Waiting for Window is loaded...")
            waitDriver.until(ExpectedConditions.visibilityOfElementLocated(By.className("session__timer")))

            //Click Konto
            driver.findElement(By.ByXPath("//*[text()='$kontonummer']/../..")).click()
            log("Depot opened.")
            Thread.sleep(1000)

            log("I'm trying to export..")
            val export = driver.findElement(By.ByXPath("//*[@data-click-action='csv.banking.link']"))
            Thread.sleep(1000)

            export.click()
            log("Exported")
            Thread.sleep(1000)

            driver.close()
        } catch (exception: Exception) {
            throw exception
        }

    }


    private fun getOptions(): ChromeOptions {
        log("Running on ${if (isWindows) "Windows" else "Linux"}")

        log("Saving file in the folder $path")

        val options = ChromeOptions()
        val prefs = mutableMapOf<String, Any>()
        prefs["download.prompt_for_download"] = false
        prefs["download.default_directory"] = path
        options.setHeadless(true)
        options.addArguments("--window-size=1920,1200")
        options.setExperimentalOption("prefs", prefs)
        return options
    }
}

fun main(args: Array<String>) {
    log("Welcome from DIBA-Sniffing-Tool")
    val sniffer = Sniffer()
    log("Sniffer was started successfully")
    sniffer.start()


    val sukejura = sukejura {
        schedule {
            daysOfWeek {
                listOf(
                        DaysOfWeek.Mon,
                        DaysOfWeek.Tue,
                        DaysOfWeek.Wed,
                        DaysOfWeek.Thu,
                        DaysOfWeek.Fri
                )
            }
            hour { Hours.H(12) }
            minute { Minutes.M(0) }
            task {
                log("Starting the sniffer...")
                sniffer.start()
                log("Finished Sniffing")
                invocations().first().apply {
                    log("Next Start: ${this.toString()}")
                }
            }
        }
        start()
    }
    sukejura.schedules.first().invocations().take(1).forEach {
        println("First start at: $it")
    }

}


