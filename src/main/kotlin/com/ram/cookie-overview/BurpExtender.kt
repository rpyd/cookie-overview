package com.ram.cookieoverview

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi

class SensitiveDataDetector : BurpExtension {
   override fun initialize(api: MontoyaApi?) {
        if (api == null) {
            return
        }

        api.extension().setName("Cookie Overview")
        api.userInterface().registerContextMenuItemsProvider(CookieContextMenu(api))
        
    }
}