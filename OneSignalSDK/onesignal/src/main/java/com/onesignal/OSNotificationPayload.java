/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

// Notification properties received from OneSignal.
public class OSNotificationPayload {
   public String notificationID;
   public String title, body;
   public JSONObject additionalData;
   public String smallIcon;
   public String largeIcon;
   public String bigPicture;
   public String smallIconAccentColor;
   public String launchURL;
   public String sound;
   public String ledColor;
   public int lockScreenVisibility = 1;
   public String groupKey;
   public String groupMessage;
   public List<ActionButton> actionButtons;
   public String fromProjectNumber;
   public BackgroundImageLayout backgroundImageLayout;
   public String collapseId;
   public int priority;
   public String rawPayload;
   
   public static class ActionButton {
      public String id;
      public String text;
      public String icon;

      public JSONObject toJSONObject() {
         JSONObject json = new JSONObject();
         try {
            json.put("id", id);
            json.put("text", text);
            json.put("icon", icon);
         }
         catch (Throwable t) {
            t.printStackTrace();
         }

         return json;
      }
   }

   public static class BackgroundImageLayout {
      public String image;
      public String titleTextColor;
      public String bodyTextColor;
   }


   public JSONObject toJSONObject() {
      JSONObject json = new JSONObject();

      try {
         json.put("notificationID", notificationID);
         json.put("title", title);
         json.put("body", body);
         if (additionalData != null)
            json.put("additionalData", additionalData);
         json.put("smallIcon", smallIcon);
         json.put("largeIcon", largeIcon);
         json.put("bigPicture", bigPicture);
         json.put("smallIconAccentColor", smallIconAccentColor);
         json.put("launchURL", launchURL);
         json.put("sound", sound);
         json.put("ledColor", ledColor);
         json.put("lockScreenVisibility", lockScreenVisibility);
         json.put("groupKey", groupKey);
         json.put("groupMessage", groupMessage);

         if (actionButtons != null) {
            JSONArray actionButtonJsonArray = new JSONArray();
            for (ActionButton actionButton : actionButtons) {
               actionButtonJsonArray.put(actionButton.toJSONObject());
            }
            json.put("actionButtons", actionButtonJsonArray);
         }
         json.put("fromProjectNumber", fromProjectNumber);
         json.put("collapseId", collapseId);
         json.put("priority", priority);

         json.put("rawPayload", rawPayload);
      }
      catch (Throwable t) {
         t.printStackTrace();
      }

      return json;
   }
}