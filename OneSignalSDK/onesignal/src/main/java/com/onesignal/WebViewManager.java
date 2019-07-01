package com.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;

import static com.onesignal.OSViewUtils.dpToPx;

// Manages WebView instances by pre-loading them, displaying them, and closing them when dismissed.
//   Includes a static map for pre-loading, showing, and dismissed so these events can't be duplicated.

// Flow for Displaying WebView
// 1. showHTMLString - Creates WebView and loads page.
// 2. Wait for JavaScriptInterface.postMessage to fire with "rendering_complete"
// 3. This calls showActivity which starts a new WebView
// 4. WebViewActivity will call WebViewManager.instanceFromIam(...) to get this instance and
//       add it's prepared WebView add add it to the Activity.

class WebViewManager extends ActivityLifecycleHandler.ActivityAvailableListener {

    private static final String TAG = WebViewManager.class.getCanonicalName();
    private static final int MARGIN_PX_SIZE = dpToPx(24);
    private static final int IN_APP_MESSAGE_INIT_DELAY = 200;

    enum Position {
        TOP_BANNER,
        BOTTOM_BANNER,
        CENTER_MODAL,
        FULL_SCREEN,
        ;

        boolean isBanner() {
            switch (this) {
                case TOP_BANNER:
                case BOTTOM_BANNER:
                    return true;
            }
            return false;
        }
    }

    @Nullable private WebView webView;
    @Nullable private InAppMessageView messageView;
    private int screenOrientation = Configuration.ORIENTATION_UNDEFINED;

    @SuppressLint("StaticFieldLeak")
    private static WebViewManager lastInstance = null;

    @NonNull private Activity activity;
    @NonNull private OSInAppMessage message;

    interface OneSignalGenericCallback {
        void onComplete();
    }

    private WebViewManager(@NonNull OSInAppMessage message, @NonNull Activity activity) {
        this.message = message;
        this.activity = activity;
    }

    /**
     * Creates a new WebView
     * Dismiss WebView if already showing one and the new one is a Preview
     *
     * @param message the message to show
     * @param htmlStr the html to display on the WebView
     */
    static void showHTMLString(@NonNull final OSInAppMessage message, @NonNull final String htmlStr) {
        final Activity currentActivity = ActivityLifecycleHandler.curActivity;
        /* IMPORTANT
         * This is the starting route for grabbing the current Activity and passing it to InAppMessageView */
        if (currentActivity != null) {

            // Only a preview will be dismissed, this prevents normal messages from being
            // removed when a preview is sent into the app
            if (lastInstance != null && message.isPreview) {
                // Created a callback for dismissing a message and preparing the next one
                lastInstance.dismissAndAwaitNextMessage(new OneSignalGenericCallback() {
                    @Override
                    public void onComplete() {
                        lastInstance = null;
                        initInAppMessage(currentActivity, message, htmlStr);
                    }
                });
            } else {
                initInAppMessage(currentActivity, message, htmlStr);
            }
            return;
        }

        /* IMPORTANT
         * Loop the setup for in app message until curActivity is not null */
        Looper.prepare();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                showHTMLString(message, htmlStr);
            }
        }, IN_APP_MESSAGE_INIT_DELAY);
    }

    private static void initInAppMessage(@NonNull final Activity currentActivity, @NonNull OSInAppMessage message, @NonNull String htmlStr) {
        try {
            final String base64Str = Base64.encodeToString(
                    htmlStr.getBytes("UTF-8"),
                    Base64.NO_WRAP
            );

            final WebViewManager webViewManager = new WebViewManager(message, currentActivity);
            lastInstance = webViewManager;

            // Web view must be created on the main thread.
            OSUtils.runOnMainUIThread(new Runnable() {
                @Override
                public void run() {
                    webViewManager.setupWebView(currentActivity, base64Str);
                }
            });
        } catch (UnsupportedEncodingException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Catch on initInAppMessage: ", e);
            e.printStackTrace();
        }
    }

    // Lets JS from the page send JSON payloads to this class
    private class OSJavaScriptInterface {

        private static final String JS_OBJ_NAME = "OSAndroid";

        @JavascriptInterface
        public void postMessage(String message) {
            try {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "OSJavaScriptInterface:postMessage: " + message);

                JSONObject jsonObject = new JSONObject(message);
                String messageType = jsonObject.getString("type");

                if (messageType.equals("rendering_complete")) {
                    handleRenderComplete(jsonObject);
                } else if (messageType.equals("resize")) {
                    handleResize(jsonObject);
                } else if (messageType.equals("action_taken")) {
                    handleActionTaken(jsonObject);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void handleResize(JSONObject jsonObject) {
            if (messageView == null)
                return;

            int pageHeight = getPageHeightData(jsonObject);
            if (pageHeight == -1)
                return;

            messageView.updateHeight(pageHeight);
        }

        private void handleRenderComplete(JSONObject jsonObject) {
            showMessageView(activity,
                    getPageHeightData(jsonObject),
                    getDisplayLocation(jsonObject)
            );
        }

        private int getPageHeightData(JSONObject jsonObject) {
            try {
                int height = jsonObject.getJSONObject("pageMetaData").getJSONObject("rect").getInt("height");
                int pxHeight = OSViewUtils.dpToPx(height);
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "getPageHeightData:pxHeight: " + pxHeight);

                int maxPxHeight = getWebViewYSize(activity);
                if (pxHeight > maxPxHeight) {
                    pxHeight = maxPxHeight;
                    OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "getPageHeightData:pxHeight is over screen max: " + maxPxHeight);
                }

                return pxHeight;
            } catch (JSONException e) {
                return -1;
            }
        }

        private Position getDisplayLocation(JSONObject jsonObject) {
            Position displayLocation = Position.FULL_SCREEN;
            try {
                if (jsonObject.has("displayLocation") && !jsonObject.get("displayLocation").equals(""))
                    displayLocation = Position.valueOf(jsonObject.optString("displayLocation", "FULL_SCREEN").toUpperCase());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return displayLocation;
        }

        private void handleActionTaken(JSONObject jsonObject) throws JSONException {
            JSONObject body = jsonObject.getJSONObject("body");
            String id = body.optString("id", null);
            if (message.isPreview) {
                OSInAppMessageController.getController().onMessageActionOccurredOnPreview(message, body);
            } else if (id != null) {
                OSInAppMessageController.getController().onMessageActionOccurredOnMessage(message, body);
            }

            boolean close = body.getBoolean("close");
            if (close)
                dismissAndAwaitNextMessage(null);
        }
    }

    @Override
    void available(@NonNull Activity activity) {
        this.activity = activity;
        OneSignal.onesignalLog(
           OneSignal.LOG_LEVEL.DEBUG,
           "ActivityAvailableListener:available: " + activity
        );

        // Important to grow the WebView to the max size so the image does not stay small
        //   when rotating from landscape to portrait. Or vice versa
        setWebViewToMaxSize(activity);
        messageView.setWebView(webView);
        messageView.showView(activity);
        messageView.checkIfShouldDismiss();

        screenOrientation = activity.getResources().getConfiguration().orientation;
    }

    @Override
    void stopped(WeakReference<Activity> reference) {
        if (messageView != null) {
            messageView.destroyView(reference);
        }
    }

    // TODO: Test with chrome://crash
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView(@NonNull final Activity currentActivity, @NonNull String base64Message) {
        enableWebViewRemoteDebugging();

        webView = new OSWebView(currentActivity);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.getSettings().setJavaScriptEnabled(true);

        // Setup receiver for page events / data from JS
        webView.addJavascriptInterface(new OSJavaScriptInterface(), OSJavaScriptInterface.JS_OBJ_NAME);

        setWebViewToMaxSize(currentActivity);

        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "getWebViewSize: " + getWebViewXSize(currentActivity) + ", " + getWebViewYSize(currentActivity));

        blurryRenderingWebViewForKitKatWorkAround(webView);
        webView.loadData(base64Message, "text/html; charset=utf-8", "base64");
    }

    private void blurryRenderingWebViewForKitKatWorkAround(@NonNull WebView webView) {
        // Android 4.4 has a rendering bug that cause the whole WebView to by extremely blurry
        // This is due to a bug with hardware rending so ensure it is disabled.
        // Tested on other version of Android and it is specific to only Android 4.4
        //    On both the emulator and real devices.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT)
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    // This sets the WebView view port sizes to the max screen sizes so the initialize
    //   max content height can be calculated.
    // A render complete or resize event will fire from JS to tell Java it's height and will then display
    //  it via this SDK's InAppMessageView class. If smaller than the screen it will correctly
    //  set it's height to match.
    private void setWebViewToMaxSize(Activity activity) {
        webView.setLeft(0);
        webView.setRight(getWebViewXSize(activity));
        webView.setTop(0);
        webView.setBottom(getWebViewYSize(activity));
    }

    private void showMessageView(Activity currentActivity, int pageHeight, Position displayLocation) {
        messageView = new InAppMessageView(webView, displayLocation, pageHeight, message.getDisplayDuration());
        messageView.setMessageController(new InAppMessageView.InAppMessageViewListener() {
            @Override
            public void onMessageWasShown() {
                OSInAppMessageController.onMessageWasShown(message);
            }

            @Override
            public void onMessageWasDismissed() {
                OSInAppMessageController.getController().messageWasDismissed(message);
                ActivityLifecycleHandler.removeActivityAvailableListener(TAG + message.messageId);
                lastInstance = null;
            }
        });

        // Fires event if available, which will call messageView.showInAppMessageView() for us.
        ActivityLifecycleHandler.setActivityAvailableListener(TAG + message.messageId, this);
    }

    // Allow Chrome Remote Debugging if OneSignal.LOG_LEVEL.DEBUG or higher
    private static void enableWebViewRemoteDebugging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                OneSignal.atLogLevel(OneSignal.LOG_LEVEL.DEBUG)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private int getWebViewXSize(Activity activity) {
        return OSViewUtils.getUsableWindowRect(activity).width() - (MARGIN_PX_SIZE * 2);
    }

    private int getWebViewYSize(Activity activity) {
        return OSViewUtils.getUsableWindowRect(activity).height() - (MARGIN_PX_SIZE * 2);
    }

    /**
     * Trigger the {@link #messageView} dismiss animation flow
     */
    private void dismissAndAwaitNextMessage(@Nullable final OneSignalGenericCallback callback) {
        if (messageView == null)
            return;

        messageView.dismissAndAwaitNextMessage(new OneSignalGenericCallback() {
            @Override
            public void onComplete() {
                messageView = null;
                if (callback != null)
                    callback.onComplete();
            }
        });
    }
}
