package com.test.onesignal;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import android.support.annotation.Nullable;

import com.onesignal.OneSignalDbHelper;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.OSSessionManager;
import com.onesignal.OneSignalPackagePrivateHelper.OneSignalPrefs;
import com.onesignal.OutcomeEvent;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowDynamicTimer;
import com.onesignal.ShadowFirebaseAnalytics;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowGcmBroadcastReceiver;
import com.onesignal.ShadowGoogleApiClientCompatProxy;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOSWebView;
import com.onesignal.ShadowOneSignalDbHelper;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowOneSignalRestClientWithMockConnection;
import com.onesignal.ShadowPushRegistratorGCM;
import com.onesignal.StaticResetHelper;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.Scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.robolectric.Shadows.shadowOf;

public class TestHelpers {

   static Exception lastException;

   static void beforeTestInitAndCleanup() throws Exception {
      OneSignalPackagePrivateHelper.OneSignalPrefs.initializePool();
      if (!ranBeforeTestSuite)
         return;

      stopAllOSThreads();

      StaticResetHelper.restSetStaticFields();

      ShadowOneSignalRestClient.resetStatics();

      ShadowPushRegistratorGCM.resetStatics();
   
      ShadowNotificationManagerCompat.enabled = true;

      ShadowOSUtils.subscribableStatus = 1;
   
      ShadowCustomTabsClient.resetStatics();
      ShadowGcmBroadcastReceiver.resetStatics();

      ShadowFusedLocationApiWrapper.resetStatics();

      ShadowFirebaseAnalytics.resetStatics();

      ShadowGoogleApiClientCompatProxy.restSetStaticFields();
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalRestClientWithMockConnection.resetStatics();

      ShadowOSWebView.resetStatics();

      ShadowDynamicTimer.resetStatics();

      ShadowOSWebView.resetStatics();

      // 100ms is default time Robolectric uses,
      //   however it does not reset back on it's own between tests.
      ShadowSystemClock.setCurrentTimeMillis(100);

      lastException = null;
   }

   static void afterTestCleanup() throws Exception {
      try {
         stopAllOSThreads();
      } catch (Exception e) {
         e.printStackTrace();
      }

      if (lastException != null)
         throw lastException;

      OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getReadableDatabase().close();
   }

   static void stopAllOSThreads() {
      boolean joinedAThread;
      do {
         joinedAThread = false;
         Set<Thread> threadSet = Thread.getAllStackTraces().keySet();

         for (Thread thread : threadSet) {
            if (thread.getName().startsWith("OS_")) {
               thread.interrupt();
               joinedAThread = true;
            }
         }
      } while (joinedAThread);
   }

   static void flushBufferedSharedPrefs() {
      OneSignalPrefs.WritePrefHandlerThread handlerThread = OneSignalPackagePrivateHelper.OneSignalPrefs.prefsHandler;

      synchronized (handlerThread.mHandler) {
         Scheduler scheduler = shadowOf(handlerThread.getLooper()).getScheduler();
         while (scheduler.runOneTask());
      }
   }

   // Join all OS_ threads
   //   Returns true if we had to join any threads
   static boolean runOSThreads() throws Exception {
      boolean createdNewThread = false;
      boolean joinedAThread;
      do {
         joinedAThread = false;
         Set<Thread> threadSet = Thread.getAllStackTraces().keySet();

         for (Thread thread : threadSet) {
            if (thread.getName().startsWith("OS_")) {
               if (ShadowOneSignalRestClient.isAFrozenThread(thread))
                  continue;

               thread.join(0, 1);

               if (lastException != null)
                  throw lastException;
               joinedAThread = createdNewThread = true;
            }
         }
      } while (joinedAThread);

      return createdNewThread;
   }

   static Thread getThreadByName(String threadName) {
      for (Thread t : Thread.getAllStackTraces().keySet()) {
         if (t.getName().equals(threadName))
            return t;
      }
      return null;
   }

   // Run any OneSignal background threads including any pending runnables
   static void threadAndTaskWait() throws Exception {
      ShadowApplication.getInstance().getForegroundThreadScheduler().runOneTask();
      // Runs Runnables posted by calling View.post() which are run on the main thread.
      Robolectric.getForegroundThreadScheduler().runOneTask();

      boolean createdNewThread;
      do {
         createdNewThread = runOSThreads();
         
         boolean advancedRunnables = OneSignalPackagePrivateHelper.runAllNetworkRunnables();
         advancedRunnables = OneSignalPackagePrivateHelper.runFocusRunnables() || advancedRunnables;
         
         if (advancedRunnables)
            createdNewThread = true;
      } while (createdNewThread);

      if (lastException != null)
         throw lastException;
   }

   private static boolean ranBeforeTestSuite;
   static void beforeTestSuite() throws Exception {
      if (ranBeforeTestSuite)
         return;

      StaticResetHelper.load();

      Looper.prepareMainLooper();

      beforeTestInitAndCleanup();

      System.out.println("beforeTestSuite!!!!!!");

      // Setup process global exception handler to catch any silent exceptions on background threads
      Thread.setDefaultUncaughtExceptionHandler(
         new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
               lastException = new Exception(e);
            }
         }
      );
      ranBeforeTestSuite = true;
   }

   static void fastColdRestartApp() throws Exception {
      stopAllOSThreads();
      flushBufferedSharedPrefs();
      StaticResetHelper.restSetStaticFields();
   }
   private static int sessionCountOffset = 1;
   static void restartAppAndElapseTimeToNextSession() throws Exception {
      stopAllOSThreads();
      flushBufferedSharedPrefs();
      StaticResetHelper.restSetStaticFields();
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 1_000 * 31 * sessionCountOffset++);
   }

   static ArrayList<HashMap<String, Object>> getAllNotificationRecords() {
      SQLiteDatabase readableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getReadableDatabase();
      Cursor cursor = readableDatabase.query(
         OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME,
         null,
         null,
         null,
         null, // group by
         null, // filter by row groups
         null, // sort order, new to old
         null // limit
      );

      ArrayList<HashMap<String, Object>> mapList = new ArrayList<>();
      while (cursor.moveToNext()) {
         HashMap<String, Object> map = new HashMap<>();
         for(int i = 0; i < cursor.getColumnCount(); i++) {
            int type = cursor.getType(i);
            String key = cursor.getColumnName(i);

             if (type == Cursor.FIELD_TYPE_INTEGER)
                map.put(key, cursor.getLong(i));
             else if (type == Cursor.FIELD_TYPE_FLOAT)
                map.put(key, cursor.getFloat(i));
             else
                map.put(key, cursor.getString(i));
         }
         mapList.add(map);
      }

      cursor.close();

      return mapList;
   }

   static List<OutcomeEvent>  getAllOutcomesRecords() {
      SQLiteDatabase readableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getReadableDatabase();
      Cursor cursor = readableDatabase.query(
              OneSignalPackagePrivateHelper.OutcomeEventsTable.TABLE_NAME,
              null,
              null,
              null,
              null, // group by
              null, // filter by row groups
              null, // sort order, new to old
              null // limit
      );

      List<OutcomeEvent> events = new ArrayList<>();
      if (cursor.moveToFirst()) {
         do {
            String notificationIds = cursor.getString(cursor.getColumnIndex(OneSignalPackagePrivateHelper.OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS));
            String name = cursor.getString(cursor.getColumnIndex(OneSignalPackagePrivateHelper.OutcomeEventsTable.COLUMN_NAME));
            String sessionString = cursor.getString(cursor.getColumnIndex(OneSignalPackagePrivateHelper.OutcomeEventsTable.COLUMN_NAME_SESSION));
            OSSessionManager.Session session = OSSessionManager.Session.fromString(sessionString);
            Long timestamp = cursor.getLong(cursor.getColumnIndex(OneSignalPackagePrivateHelper.OutcomeEventsTable.COLUMN_NAME_TIMESTAMP));

            int paramsIndex = cursor.getColumnIndex(OneSignalPackagePrivateHelper.OutcomeEventsTable.COLUMN_NAME_PARAMS);
            String paramsString = cursor.isNull(paramsIndex) ? null : cursor.getString(paramsIndex);
            OneSignalPackagePrivateHelper.OutcomeParams params =
                    paramsString != null ? OneSignalPackagePrivateHelper.OutcomeParams.Builder
                            .newInstance()
                            .setJsonString(paramsString)
                            .build() : null;

            try {
               OutcomeEvent event = new OutcomeEvent(session, new JSONArray(notificationIds), name, timestamp, params);
               events.add(event);
            } catch (JSONException e) {
               e.printStackTrace();
            }
         } while (cursor.moveToNext());
      }

      cursor.close();
      readableDatabase.close();

      return events;
   }

   static void advanceTimeByMs(long advanceBy) {
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() +  advanceBy);
   }

   public static void assertMainThread() {
      if (!Looper.getMainLooper().getThread().equals(Thread.currentThread()))
         Assert.fail("assertMainThread - Not running on main thread when expected to!");
   }


   public static @Nullable JobInfo getNextJob() {
      JobScheduler jobScheduler =
         (JobScheduler)RuntimeEnvironment.application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      List<JobInfo> jobs = jobScheduler.getAllPendingJobs();
      if (jobs.size() == 0)
         return null;
      return jobs.get(0);
   }

   public static void runNextJob() {
      try {
         Class jobClass = Class.forName(getNextJob().getService().getClassName());
         JobService jobService = (JobService)Robolectric.buildService(jobClass).create().get();
         jobService.onStartJob(null);
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }
   }
}
