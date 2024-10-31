package com.ram.cookieoverview

import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.MontoyaApi
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.InvocationType
import java.awt.Component
import javax.swing.JMenuItem
import javax.swing.JFileChooser
import java.io.File
import java.io.FileNotFoundException


class CookieDatabaseCSV(private val filePath: String? = null) {
    data class CookieData(
        val platform: String,
        val category: String,
        val cookieDataKeyName: String,
        val description: String,
    )

    private val cookieMap: Map<String, CookieData>

    init {
        cookieMap = parseCSV(filePath)
    }

    private fun parseCSV(filePath: String?): Map<String, CookieData> {
        val cookieList = mutableListOf<CookieData>()

        val inputStream = if (filePath != null) {
            File(filePath).inputStream()
        } else {
            CookieDatabaseCSV::class.java.getResourceAsStream("/open-cookie-database.csv")
                ?: throw FileNotFoundException("Resource not found: /open-cookie-database.csv")
        }

        inputStream.bufferedReader().useLines { lines ->
            // Skip the header line
            lines.drop(1).forEach { line ->
                val values = line.split(",")

                val cookieData = CookieData(
                    platform = values[1],
                    category = values[2],
                    cookieDataKeyName = values[3].lowercase(),  // Store as lowercase for case-insensitive matching
                    description = values[5],
                )

                cookieList.add(cookieData)
            }
        }

        // Convert the list to a map for fast lookup
        return cookieList.associateBy { it.cookieDataKeyName }
    }

    fun getCookieData(cookieName: String): CookieData? {
        return cookieMap[cookieName.lowercase()]  // Use lowercase for case-insensitive lookup
    }
}

class CookieContextMenu(private val api: MontoyaApi) : ContextMenuItemsProvider {
    private var userProvidedFilePath: String? = null

    override fun provideMenuItems(event: ContextMenuEvent): List<Component>? {
        if (!event.isFrom(InvocationType.PROXY_HISTORY) && !event.isFrom(InvocationType.SITE_MAP_TREE)) {
            return null
        }

        val menuItem = JMenuItem("Identify unique cookies")
        val chooseMenuItem = JMenuItem("Select OpenCookieDatabase CSV file and identify unique cookies")

        chooseMenuItem.addActionListener {
            val fileChooser = JFileChooser()
            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                userProvidedFilePath = fileChooser.selectedFile.absolutePath
                api.logging().logToOutput("Selected file: $userProvidedFilePath\n\n")
                
                // Extract and process cookies with the user-provided file
                val uniqueCookies = extractUniqueCookies()
                val database = CookieDatabaseCSV(userProvidedFilePath)
                processCookies(uniqueCookies, database)
            }
        }

        menuItem.addActionListener {
            api.logging().logToOutput("Using OpenCookieDatabase CSV file bundled with extension\n\n")
            val uniqueCookies = extractUniqueCookies()
            val database = CookieDatabaseCSV(userProvidedFilePath)
            processCookies(uniqueCookies, database)
        }

        return listOf(menuItem, chooseMenuItem)
    }

    private fun extractUniqueCookies(): Set<String> {
        val uniqueCookies = mutableSetOf<String>()  // Set to store unique cookie names

        api.proxy().history()
            .asSequence()
            .flatMap { it.request().parameters(HttpParameterType.COOKIE) }
            .forEach { cookie ->
                uniqueCookies.add(cookie.name()) 
            }

        return uniqueCookies
    }

    private fun processCookies(uniqueCookies: Set<String>, database: CookieDatabaseCSV) {
        val knownCookies = mutableListOf<String>()
        val unknownCookies = mutableListOf<String>()

        uniqueCookies.forEach { cookieName ->
            val cookieData = database.getCookieData(cookieName)
            if (cookieData != null) {
                knownCookies.add(
                    "Name: ${cookieData.cookieDataKeyName}\n" +
                    "Description: ${cookieData.description}\n" +
                    "Platform: ${cookieData.platform}"
                )
            } else {
                unknownCookies.add(cookieName)
            }
        }

        val outputBuilder = StringBuilder()
        outputBuilder.append("---- All Cookies ----\n")
        outputBuilder.append(uniqueCookies.joinToString("\n"))
        outputBuilder.append("\n\n---- Known Cookies ----\n")
        outputBuilder.append(knownCookies.joinToString("\n\n"))
        outputBuilder.append("\n\n---- Unknown Cookies ----\n")
        outputBuilder.append(unknownCookies.joinToString("\n"))

        // Output the result to the Burp Suite console
        api.logging().logToOutput(outputBuilder.toString())
    }
}
