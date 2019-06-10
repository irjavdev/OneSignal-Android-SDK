package com.onesignal;

import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;

import com.onesignal.OSTrigger.OSTriggerOperatorType;
import com.onesignal.OSTriggerController.OSDynamicTriggerType;

class OSDynamicTriggerController {

    interface OSDynamicTriggerControllerObserver {
        // Alerts the observer that a trigger timer has fired
        void messageTriggerConditionChanged();
    }

    private final OSDynamicTriggerControllerObserver observer;

    private static final double REQUIRED_ACCURACY = 0.3;

    private final ArrayList<String> scheduledMessages;

    static Date sessionLaunchTime = new Date();

    OSDynamicTriggerController(OSDynamicTriggerControllerObserver triggerObserver) {
        scheduledMessages = new ArrayList<>();
        observer = triggerObserver;
    }

    boolean dynamicTriggerShouldFire(OSTrigger trigger) {
        if (trigger.value == null)
            return false;

        synchronized (scheduledMessages) {
            // All time-based trigger values should be numbers (either timestamps or offsets)
            if (!(trigger.value instanceof Number))
                return false;
            long requiredTimeInterval = (long) (((Number) trigger.value).doubleValue() * 1_000);
            long offset;

            OSDynamicTriggerType property = OSDynamicTriggerType.fromString(trigger.property);

            long currentTimeInterval = 0;

            switch (property) {
                case SESSION_DURATION:
                case PLAYTIME:
                    currentTimeInterval = new Date().getTime() - sessionLaunchTime.getTime();
                    break;
                case TIME:
                    currentTimeInterval = new Date().getTime();
                    break;
            }

            if (evaluateTimeIntervalWithOperator(requiredTimeInterval, currentTimeInterval, trigger.operatorType))
                return true;

            offset = requiredTimeInterval - currentTimeInterval;

            if (offset <= 0L)
                return false;

            // Prevents re-scheduling timers for messages that we're already waiting on
            if (scheduledMessages.contains(trigger.triggerId))
                return false;

            OSDynamicTriggerTimer.scheduleTrigger(new TimerTask() {
                @Override
                public void run() {
                    observer.messageTriggerConditionChanged();
                }
            }, trigger.triggerId, offset);

            scheduledMessages.add(trigger.triggerId);
        }

        return false;
    }

    private static boolean evaluateTimeIntervalWithOperator(double timeInterval, double currentTimeInterval, OSTriggerOperatorType operator) {
        switch (operator) {
            case LESS_THAN:
                return currentTimeInterval < timeInterval;
            case LESS_THAN_OR_EQUAL_TO:
                return currentTimeInterval <= timeInterval || roughlyEqual(timeInterval, currentTimeInterval);
            case GREATER_THAN:
                return currentTimeInterval > timeInterval;
            case GREATER_THAN_OR_EQUAL_TO:
                return currentTimeInterval >= timeInterval || roughlyEqual(timeInterval, currentTimeInterval);
            case EQUAL_TO:
                return roughlyEqual(timeInterval, currentTimeInterval);
            case NOT_EQUAL_TO:
                return !roughlyEqual(timeInterval, currentTimeInterval);
            default:
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Attempted to apply an invalid operator on a time-based in-app-message trigger: " + operator.toString());
                return false;
        }
    }

    private static boolean roughlyEqual(double left, double right) {
        return Math.abs(left - right) < REQUIRED_ACCURACY;
    }
}
