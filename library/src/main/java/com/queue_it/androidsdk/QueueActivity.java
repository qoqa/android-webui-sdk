package com.queue_it.androidsdk;

import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import java.net.MalformedURLException;
import java.net.URL;

public class QueueActivity extends AppCompatActivity {

    private String queueUrl;
    private String targetUrl;

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putString("queueUrl", queueUrl);
        outState.putString("targetUrl", targetUrl);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue);

        final Toolbar toolbar = findViewById(R.id.queueToolbar);
        final ProgressBar progressBar = findViewById(R.id.queueProgressBar);
        final WebView webView = findViewById(R.id.queueWebView);

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if (extras == null) {
                queueUrl = null;
                targetUrl = null;
            } else {
                queueUrl = extras.getString("queueUrl");
                targetUrl = extras.getString("targetUrl");
            }
        } else {
            queueUrl = (String) savedInstanceState.getSerializable("queueUrl");
            targetUrl = (String) savedInstanceState.getSerializable("targetUrl");
        }

        final URL target;
        final URL queue;
        try {
            target = new URL(targetUrl);
            queue = new URL(queueUrl);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        Log.v("QueueITEngine", "Loading initial URL: " + queueUrl);

        final Application application = getApplication();
        if (application instanceof UserAgentProvider) {
            final String userAgent = ((UserAgentProvider) application).getUserAgent();
            if (userAgent != null) {
                Log.v("QueueITEngine", "Set Webview UserAgent to: " + userAgent);
                webView.getSettings().setUserAgentString(userAgent);
            }
        }

        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient()
        {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                Log.v("Progress", Integer.toString(newProgress));
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
                progressBar.setProgress(newProgress);
                super.onProgressChanged(view, newProgress);
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                String errorMessage;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    errorMessage = String.format("%s %s: %s %s", request.getMethod(), request.getUrl(), errorResponse.getStatusCode(), errorResponse.getReasonPhrase());
                } else {
                    errorMessage = errorResponse.toString();
                }
                Log.v("QueueActivity", String.format("%s: %s", "onReceivedHttpError", errorMessage));
                super.onReceivedHttpError(view, request, errorResponse);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                String errorMessage;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    errorMessage = String.format("%s %s: %s %s", request.getMethod(), request.getUrl(), error.getErrorCode(), error.getDescription());
                } else {
                    errorMessage = error.toString();
                }
                Log.v("QueueActivity", String.format("%s: %s", "onReceivedError", errorMessage));
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(QueueActivity.this);
                builder.setMessage(R.string.notification_error_ssl_cert_invalid);
                builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handler.proceed();
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handler.cancel();
                    }
                });
                final AlertDialog dialog = builder.create();
                dialog.show();
            }

            public boolean shouldOverrideUrlLoading(WebView view, String urlString) {
                Log.v("QueueITEngine", "URL loading: " + urlString);

                URL url;
                try {
                    url = new URL(urlString);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }

                boolean isQueueDomain = url.getHost().equals(queue.getHost());
                boolean isTargetDomain = url.getHost().equals(target.getHost());

                if (isQueueDomain) {
                    broadcastChangedQueueUrl(urlString);
                }

                if (isTargetDomain && url.getPath().equals(target.getPath())) {
                    Uri uri = Uri.parse(urlString);
                    String queueItToken = uri.getQueryParameter("queueittoken");

                    broadcastQueuePassed(queueItToken);
                    disposeWebview(webView);
                    return true;
                }

                if (!isTargetDomain && !isQueueDomain) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlString));
                    startActivity(browserIntent);
                    disposeWebview(webView);
                    return true;
                }

                return false;
            }});
        webView.loadUrl(queueUrl);
    }

    private void broadcastChangedQueueUrl(String urlString) {
        Intent intentChangedQueueUrl = new Intent("on-changed-queue-url");
        intentChangedQueueUrl.putExtra("url", urlString);
        LocalBroadcastManager.getInstance(QueueActivity.this).sendBroadcast(intentChangedQueueUrl);
    }

    private void broadcastQueuePassed(String queueItToken) {
        Intent intent = new Intent("on-queue-passed");
        intent.putExtra("queue-it-token", queueItToken);
        LocalBroadcastManager.getInstance(QueueActivity.this).sendBroadcast(intent);
    }

    private void disposeWebview(WebView webView) {
        webView.loadUrl("about:blank");
        finish();
    }
}
