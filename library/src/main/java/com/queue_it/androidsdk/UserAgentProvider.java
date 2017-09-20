package com.queue_it.androidsdk;

/**
 * Permit Application to change the user agent use by the WebView.
 * Implement the interface on the Application class.
 */
public interface UserAgentProvider {
    String getUserAgent();
}
