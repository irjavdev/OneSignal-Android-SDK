package com.onesignal;

import com.onesignal.OSTrigger.OSTriggerOperatorType;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;

import com.onesignal.OSTriggerController.OSDynamicTriggerType;

class OSDynamicTriggerController {

    interface OSDynamicTriggerControllerObserver {

        //alerts the observer that a trigger timer has fired
        void messageTriggerConditionChanged();
    }

    final OSDynamicTriggerControllerObserver observer;

    private static final double REQUIRED_ACCURACY = 0.3;

    private ArrayList<String> scheduledMessages;

    static Date sessionLaunchTime = new Date();

    OSDynamicTriggerController(OSDynamicTriggerControllerObserver triggerObserver) {
        scheduledMessages = new ArrayList<>();
        observer = triggerObserver;
    }

    boolean dynamicTriggerShouldFire(OSTrigger trigger, String messageId) {
        if (trigger.value == null)
            return false;

        synchronized (scheduledMessages) {

            // All time-based trigger values should be numbers (either timestamps or offsets)
            if (!(trigger.value instanceof Number))
                return false;

            // prevents us from re-evaluating triggers we've already evaluated
            if (scheduledMessages.contains(trigger.triggerId))
                return false;

            long requiredTimeInterval = (long)(((Number)trigger.value).doubleValue() * 1000);

            long offset = 0;

            if (OSDynamicTriggerType.SESSION_DURATION.toString().equals(trigger.property)) {

                long currentDuration = (new Date()).getTime() - sessionLaunchTime.getTime();

                if (evaluateTimeIntervalWithOperator(requiredTimeInterval, currentDuration, trigger.operatorType))
                    return true;

                offset = requiredTimeInterval - currentDuration;
            } else if (OSDynamicTriggerType.TIME.toString().equals(trigger.property)) {
                long currentTimestamp = new Date().getTime();

                if (evaluateTimeIntervalWithOperator(requiredTimeInterval, currentTimestamp, trigger.operatorType))
                    return true;

                offset = requiredTimeInterval - currentTimestamp;
            }

            if (offset <= 0.0f)
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

    boolean evaluateTimeIntervalWithOperator(double timeInterval, double currentTimeInterval, OSTriggerOperatorType operator) {
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

    boolean roughlyEqual(double left, double right) {
        return Math.abs(left - right) < REQUIRED_ACCURACY;
    }
}
