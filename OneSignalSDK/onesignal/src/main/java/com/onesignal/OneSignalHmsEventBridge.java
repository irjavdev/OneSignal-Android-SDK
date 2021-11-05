package com.onesignal;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.huawei.hms.push.RemoteMessage;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * If you have your own {@link com.huawei.hms.push.HmsMessageService} defined in your app please also
 * call {@link OneSignalHmsEventBridge#onNewToken} and {@link OneSignalHmsEventBridge#onMessageReceived}
 * as this is required for some OneSignal features.
 * If you don't have a class that extends from {@link com.huawei.hms.push.HmsMessageService}
 * or anther SDK / Library that handles HMS push then you don't need to use this class.
 * OneSignal automatically gets these events.
 */
public class OneSignalHmsEventBridge {

    private static final AtomicBoolean firstToken = new AtomicBoolean(true);

    /**
     * Method used by last HMS push version 5.3.0.304 and upper
     */
    public static void onNewToken(@NonNull Context context, @NonNull String token, @Nullable Bundle bundle) {
        if (firstToken.compareAndSet(true, false)) {
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "OneSignalHmsEventBridge onNewToken - HMS token: " + token + " Bundle: " + bundle);
            PushRegistratorHMS.fireCallback(token);
        } else {
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "OneSignalHmsEventBridge ignoring onNewToken - HMS token: " + token + " Bundle: " + bundle);
        }
    }

    /**
     * This method is being deprecated
     * @see OneSignalHmsEventBridge#onNewToken(Context, String, Bundle)
     */
    @Deprecated
    public static void onNewToken(@NonNull Context context, @NonNull String token) {
        onNewToken(context, token, null);
    }

    public static void onMessageReceived(@NonNull Context context, @NonNull RemoteMessage message) {
        NotificationPayloadProcessorHMS.processDataMessageReceived(context, message.getData());
    }
}
