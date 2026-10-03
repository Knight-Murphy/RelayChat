package com.relaychat.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.relaychat.app.MainActivity;
import com.relaychat.app.model.ProviderProfile;
import com.relaychat.app.network.ApiClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ProviderEditorScreen extends LinearLayout {
    private final MainActivity host;
    private final ProviderProfile original;
    private final EditText nameInput;
    private final EditText baseUrlInput;
    private final EditText apiKeyInput;
    private final Spinner modeSpinner;
    private final EditText modelsInput;
    private final EditText selectedModelInput;
    private final EditText systemPromptInput;
    private final Button fetchButton;

    public ProviderEditorScreen(Context context, MainActivity host, ProviderProfile provider) {
        super(context);
        this.host = host;
        this.original = provider;
        setOrientation(VERTICAL);
        setBackgroundColor(UiKit.CANVAS);
        addView(buildHeader());

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(UiKit.dp(context, 12), UiKit.dp(context, 12),
                UiKit.dp(context, 12), UiKit.dp(context, 20));
        LinearLayout content = UiKit.vertical(context);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nameInput = addField(content, "服务商名称", provider == null ? "" : provider.getName(),
                "例如：我的中转站", false);
        baseUrlInput = addField(content, "API 地址", provider == null ? "https://api.openai.com/v1" : provider.getBaseUrl(),
                "填到 /v1，例如 https://api.example.com/v1", false);
        apiKeyInput = addField(content, "API Key", provider == null ? "" : provider.getApiKey(),
                "仅保存在本机", false);
        apiKeyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        TextView modeLabel = fieldLabel("接口模式");
        content.addView(modeLabel, UiKit.matchWrap(UiKit.dp(context, 12), UiKit.dp(context, 4)));
        modeSpinner = UiKit.spinner(context, Arrays.asList(
                "Chat Completions（兼容范围广）", "Responses（OpenAI 新接口）"));
        modeSpinner.setSelection(provider != null && ProviderProfile.MODE_RESPONSES.equals(provider.getMode()) ? 1 : 0, false);
        content.addView(modeSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        modelsInput = addField(content, "模型列表", provider == null ? "" : joinModels(provider.getModels()),
                "每行一个模型 ID", true);
        selectedModelInput = addField(content, "当前模型", provider == null ? "" : provider.getSelectedModel(),
                "填写或从模型列表中选择", false);
        systemPromptInput = addField(content, "系统提示词",
                provider == null ? "You are a helpful assistant." : provider.getSystemPrompt(),
                "可留空使用默认值", true);

        LinearLayout actions = UiKit.horizontal(context);
        actions.setGravity(android.view.Gravity.END);
        fetchButton = UiKit.compactButton(context, "拉取模型", false);
        fetchButton.setOnClickListener(view -> fetchModels());
        actions.addView(fetchButton);
        Button save = UiKit.button(context, "保存配置", true);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.leftMargin = UiKit.dp(context, 8);
        save.setOnClickListener(view -> save());
        actions.addView(save, saveParams);
        content.addView(actions, UiKit.matchWrap(UiKit.dp(context, 18), 0));

        TextView tip = UiKit.text(context,
                "地址必须使用 HTTPS。若中转站只支持 http://localhost，请在桌面环境中使用其他客户端。",
                12, UiKit.MUTED);
        content.addView(tip, UiKit.matchWrap(UiKit.dp(context, 14), 0));
    }

    private View buildHeader() {
        LinearLayout header = UiKit.horizontal(getContext());
        header.setPadding(UiKit.dp(getContext(), 10), UiKit.dp(getContext(), 8),
                UiKit.dp(getContext(), 12), UiKit.dp(getContext(), 8));
        header.setBackgroundColor(UiKit.SURFACE);
        header.setElevation(UiKit.dp(getContext(), 2));
        Button back = UiKit.compactButton(getContext(), "返回", false);
        back.setOnClickListener(view -> host.showSettings());
        header.addView(back);
        TextView title = UiKit.heading(getContext(), original == null ? "新增服务商" : "编辑服务商", 18);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = UiKit.dp(getContext(), 12);
        header.addView(title, params);
        return header;
    }

    private TextView fieldLabel(String text) {
        TextView label = UiKit.text(getContext(), text, 13, UiKit.INK);
        label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return label;
    }

    private EditText addField(LinearLayout content, String label, String value, String hint, boolean multiline) {
        content.addView(fieldLabel(label), UiKit.matchWrap(UiKit.dp(getContext(), 12), UiKit.dp(getContext(), 4)));
        EditText input = UiKit.input(getContext(), hint, multiline);
        input.setText(value == null ? "" : value);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void fetchModels() {
        String baseUrl = baseUrlInput.getText().toString().trim();
        String apiKey = apiKeyInput.getText().toString().trim();
        if (!baseUrl.startsWith("https://")) {
            host.toast("API 地址必须是 HTTPS");
            return;
        }
        ProviderProfile temporary = new ProviderProfile(
                original == null ? "temporary" : original.getId(),
                nameInput.getText().toString(),
                baseUrl,
                apiKey,
                modeSpinner.getSelectedItemPosition() == 1
                        ? ProviderProfile.MODE_RESPONSES : ProviderProfile.MODE_CHAT,
                new ArrayList<>(), selectedModelInput.getText().toString(), "");
        fetchButton.setEnabled(false);
        fetchButton.setText("获取中");
        host.getApiClient().fetchModels(temporary, new ApiClient.ModelCallback() {
            @Override
            public void onSuccess(List<String> models) {
                postToHost(() -> {
                    modelsInput.setText(joinModels(models));
                    if (selectedModelInput.getText().toString().trim().isEmpty() && !models.isEmpty()) {
                        selectedModelInput.setText(models.get(0));
                    }
                    fetchButton.setEnabled(true);
                    fetchButton.setText("拉取模型");
                    host.toast("已获取 " + models.size() + " 个模型");
                });
            }

            @Override
            public void onError(String message) {
                postToHost(() -> {
                    fetchButton.setEnabled(true);
                    fetchButton.setText("拉取模型");
                    host.toast(message);
                });
            }
        });
    }

    private void save() {
        ProviderProfile provider = original == null ? new ProviderProfile() : original;
        provider.setName(nameInput.getText().toString());
        provider.setBaseUrl(baseUrlInput.getText().toString());
        provider.setApiKey(apiKeyInput.getText().toString());
        provider.setMode(modeSpinner.getSelectedItemPosition() == 1
                ? ProviderProfile.MODE_RESPONSES : ProviderProfile.MODE_CHAT);
        List<String> models = parseModels(modelsInput.getText().toString());
        provider.setModels(models);
        String selected = selectedModelInput.getText().toString().trim();
        provider.setSelectedModel(selected.isEmpty() && !models.isEmpty() ? models.get(0) : selected);
        provider.setSystemPrompt(systemPromptInput.getText().toString());

        if (provider.getBaseUrl().isEmpty() || !provider.getBaseUrl().startsWith("https://")) {
            host.toast("请填写有效的 HTTPS API 地址");
            return;
        }
        if (models.isEmpty() && provider.getSelectedModel().isEmpty()) {
            host.toast("请至少填写一个模型名称");
            return;
        }
        if (provider.getSelectedModel().isEmpty()) {
            host.toast("请选择当前模型");
            return;
        }
        host.saveProvider(provider);
        host.toast("配置已保存");
        host.showSettings();
    }

    private void postToHost(Runnable action) {
        if (!host.isAlive()) {
            return;
        }
        host.runOnUiThread(() -> {
            if (host.isAlive()) {
                action.run();
            }
        });
    }

    private List<String> parseModels(String value) {
        List<String> models = new ArrayList<>();
        for (String line : value.split("[\\n,]")) {
            String model = line.trim();
            if (!model.isEmpty() && !models.contains(model)) {
                models.add(model);
            }
        }
        return models;
    }

    private String joinModels(List<String> models) {
        StringBuilder result = new StringBuilder();
        for (String model : models) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(model);
        }
        return result.toString();
    }
}
