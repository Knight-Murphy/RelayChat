package com.relaychat.app.model;

import com.relaychat.app.data.SecretStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ProviderProfile {
    public static final String MODE_CHAT = "chat_completions";
    public static final String MODE_RESPONSES = "responses";

    private String id;
    private String name;
    private String baseUrl;
    private String apiKey;
    private String mode;
    private final List<String> models;
    private String selectedModel;
    private String systemPrompt;

    public ProviderProfile() {
        this(UUID.randomUUID().toString(), "新服务商", "", "", MODE_CHAT,
                new ArrayList<>(), "", "You are a helpful assistant.");
    }

    public ProviderProfile(String id, String name, String baseUrl, String apiKey,
                           String mode, List<String> models, String selectedModel,
                           String systemPrompt) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.name = safe(name, "新服务商");
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.mode = MODE_RESPONSES.equals(mode) ? MODE_RESPONSES : MODE_CHAT;
        this.models = new ArrayList<>();
        if (models != null) {
            for (String model : models) {
                addModel(this.models, model);
            }
        }
        this.selectedModel = safe(selectedModel, this.models.isEmpty() ? "" : this.models.get(0));
        this.systemPrompt = safe(systemPrompt, "You are a helpful assistant.");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = safe(name, "未命名服务商");
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = MODE_RESPONSES.equals(mode) ? MODE_RESPONSES : MODE_CHAT;
    }

    public List<String> getModels() {
        return Collections.unmodifiableList(models);
    }

    public void setModels(List<String> values) {
        models.clear();
        if (values != null) {
            for (String model : values) {
                addModel(models, model);
            }
        }
        if (!models.isEmpty() && (selectedModel == null || !models.contains(selectedModel))) {
            selectedModel = models.get(0);
        }
    }

    public String getSelectedModel() {
        return selectedModel;
    }

    public void setSelectedModel(String selectedModel) {
        this.selectedModel = selectedModel == null ? "" : selectedModel.trim();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = safe(systemPrompt, "You are a helpful assistant.");
    }

    public String endpoint(String leaf) {
        String cleanLeaf = leaf.startsWith("/") ? leaf : "/" + leaf;
        return baseUrl + cleanLeaf;
    }

    public JSONObject toJson(SecretStore secretStore) throws Exception {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("baseUrl", baseUrl);
        object.put("key", secretStore.encrypt(apiKey));
        object.put("mode", mode);
        object.put("models", new JSONArray(models));
        object.put("selectedModel", selectedModel);
        object.put("systemPrompt", systemPrompt);
        return object;
    }

    public static ProviderProfile fromJson(JSONObject object, SecretStore secretStore) {
        List<String> models = new ArrayList<>();
        JSONArray modelArray = object.optJSONArray("models");
        if (modelArray != null) {
            for (int index = 0; index < modelArray.length(); index++) {
                addModel(models, modelArray.optString(index));
            }
        }
        String apiKey = object.optString("apiKey", "");
        if (apiKey.isEmpty()) {
            apiKey = secretStore.decrypt(object.optString("key", ""));
        }
        return new ProviderProfile(
                object.optString("id", UUID.randomUUID().toString()),
                object.optString("name", "未命名服务商"),
                object.optString("baseUrl", ""),
                apiKey,
                object.optString("mode", MODE_CHAT),
                models,
                object.optString("selectedModel", ""),
                object.optString("systemPrompt", "You are a helpful assistant."));
    }

    private static String normalizeBaseUrl(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        String[] endpoints = {"/chat/completions", "/responses", "/models"};
        for (String endpoint : endpoints) {
            if (result.endsWith(endpoint)) {
                result = result.substring(0, result.length() - endpoint.length());
                break;
            }
        }
        return result;
    }

    private static void addModel(List<String> target, String model) {
        String clean = model == null ? "" : model.trim();
        if (!clean.isEmpty() && !target.contains(clean)) {
            target.add(clean);
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
