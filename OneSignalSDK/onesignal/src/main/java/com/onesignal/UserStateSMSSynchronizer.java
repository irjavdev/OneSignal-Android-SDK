package com.onesignal;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UserStateSMSSynchronizer extends UserStateSecondaryChannelSynchronizer {

    UserStateSMSSynchronizer() {
        super(OneSignalStateSynchronizer.UserStateSynchronizerType.SMS);
    }

    @Override
    protected UserState newUserState(String inPersistKey, boolean load) {
        return new UserStateSMS(inPersistKey, load);
    }

    @Override
    protected String getId() {
        return OneSignal.getSMSId();
    }

    @Override
    void logoutEmail() {
    }

    @Override
    void logoutSMS() {
        OneSignal.saveSMSId("");

        resetCurrentState();
        getToSyncUserState().removeFromSyncValues("identifier");
        List<String> keysToRemove = new ArrayList<>();
        keysToRemove.add("sms_auth_hash");
        keysToRemove.add("device_player_id");
        keysToRemove.add("external_user_id");
        getToSyncUserState().removeFromSyncValues(keysToRemove);
        getToSyncUserState().persistState();

        OneSignal.getSMSSubscriptionState().clearSMSAndId();
    }

    @Override
    protected String getChannelKey() {
        return SMS_NUMBER_KEY;
    }

    @Override
    protected String getAuthHashKey() {
        return SMS_AUTH_HASH_KEY;
    }

    @Override
    protected int getDeviceType() {
        return UserState.DEVICE_TYPE_SMS;
    }

    @Override
    void fireUpdateSuccess(JSONObject result) {
        OneSignal.fireSMSUpdateSuccess(result);
    }

    @Override
    void fireUpdateFailure() {
        OneSignal.fireSMSUpdateFailure();
    }

    @Override
    void updateIdDependents(String id) {
        OneSignal.updateSMSIdDependents(id);
    }

}
