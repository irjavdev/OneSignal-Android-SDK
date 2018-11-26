package com.test.onesignal;


import android.annotation.SuppressLint;
import android.app.Activity;
import com.onesignal.BuildConfig;
import com.onesignal.OSTrigger;
import com.onesignal.OneSignal;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGCM;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;
import com.onesignal.OSInAppMessage;
import com.onesignal.OSTrigger.OSTriggerOperatorType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.Field;
import java.util.HashMap;
import static junit.framework.Assert.*;

@Config(packageName = "com.onesignal.example",
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowPushRegistratorGCM.class,
                ShadowOSUtils.class,
                ShadowAdvertisingIdProviderGPS.class,
                ShadowCustomTabsClient.class,
                ShadowCustomTabsSession.class,
                ShadowNotificationManagerCompat.class,
                ShadowJobService.class
        },
        instrumentedPackages = {"com.onesignal"},
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class InAppMessagingTests {
    public static OSInAppMessage message;

    public static final String testMessageId = "a4b3gj7f-d8cc-11e4-bed1-df8f05be55ba";
    public static final String testContentId = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f";
    private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    @BeforeClass
    public static void setupClass() throws Exception {
        ShadowLog.stream = System.out;

        buildTestMessage();

        TestHelpers.beforeTestSuite();

        Field OneSignal_CurrentSubscription = OneSignal.class.getDeclaredField("subscribableStatus");
        OneSignal_CurrentSubscription.setAccessible(true);

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();

    }

    static void buildTestMessage() {
        // builds a test message to test JSON parsing constructor of OSInAppMessage
        JSONObject json = new JSONObject();

        try {
            json.put("id", testMessageId);
            json.put("content_id", testContentId);
            json.put("max_display_time", 30);

            JSONArray orTriggers = new JSONArray();
            JSONArray andTriggers = new JSONArray();

            JSONObject triggerJson = new JSONObject();
            triggerJson.put("property", "os_session_duration");
            triggerJson.put("operator", ">=");
            triggerJson.put("value", 3);

            andTriggers.put(triggerJson);
            orTriggers.put(andTriggers);
            json.put("triggers", orTriggers);

            message = new OSInAppMessage(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBuiltMessage() {
        assertEquals(message.messageId, testMessageId);
        assertEquals(message.contentId, testContentId);
        assertEquals(message.maxDisplayTime, 30.0);
    }

    @Test
    public void testBuiltMessageTrigger() {
        OSTrigger trigger = message.triggers.get(0).get(0);

        assertEquals(trigger.operatorType, OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO);
        assertEquals(trigger.property, "os_session_duration");
        assertEquals(trigger.value, 3);
    }

    @Test
    public void testSaveMultipleTriggerValues() {
        OneSignalInit();

        HashMap<String, Object> testTriggers = new HashMap<>();
        testTriggers.put("test1", "value1");
        testTriggers.put("test2", "value2");

        OneSignal.addTriggers(testTriggers);

        assertEquals(OneSignal.getTriggerValueForKey("test1"), "value1");
        assertEquals(OneSignal.getTriggerValueForKey("test2"), "value2");
    }

    @Test
    public void testDeleteSavedTriggerValue() {
        OneSignalInit();

        OneSignal.addTrigger("test1", "value1");

        assertEquals(OneSignal.getTriggerValueForKey("test1"), "value1");

        OneSignal.removeTriggerforKey("test1");

        assertNull(OneSignal.getTriggerValueForKey("test1"));
    }

    private void OneSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
        blankActivityController.resume();
    }
}
