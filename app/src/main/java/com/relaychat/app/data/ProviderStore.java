package com.relaychat.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.relaychat.app.model.ProviderProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ProviderStore {
    private static final String PREFS_NAME = "relaychat_settings";
    private static final String KEY_PROVIDERS = "providers";
    private static final String KEY_SELECTED = "selected_provider";

    private final SharedPreferences preferences;
    private final SecretStore secretStore;

    public ProviderStore(Context context, SecretStore secretStore) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.secretStore = secretStore;
    }

    public synchronized List<ProviderProfile> loadProviders() {
        List<ProviderProfile> providers = new ArrayList<>();
        String raw = preferences.getString(KEY_PROVIDERS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object != null) {
                    providers.add(ProviderProfile.fromJson(object, secretStore));
                }
            }
        } catch (Exception ignored) {
            // Corrupt metadata should not prevent the app from opening.
        }
        return providers;
    }

    public synchronized void saveProviders(List<ProviderProfile> providers) {
        JSONArray array = new JSONArray();
        try {
            for (ProviderProfile provider : providers) {
                array.put(provider.toJson(secretStore));
            }
            preferences.edit().putString(KEY_PROVIDERS, array.toString()).apply();
        } catch (Exception error) {
            throw new IllegalStateException("无法保存服务商配置", error);
        }
    }

    public synchronized String getSelectedProviderId() {
        return preferences.getString(KEY_SELECTED, "");
    }

    public synchronized void setSelectedProviderId(String providerId) {
        preferences.edit().putString(KEY_SELECTED, providerId == null ? "" : providerId).apply();
    }
}
