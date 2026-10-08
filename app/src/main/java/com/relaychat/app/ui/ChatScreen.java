package com.relaychat.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.relaychat.app.MainActivity;
import com.relaychat.app.media.ImagePicker;
import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.ImageAttachment;
import com.relaychat.app.model.ProviderProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatScreen extends LinearLayout {
    private static final int MAX_ATTACHMENTS = 4;
    private static final LruCache<String, Bitmap> THUMBNAILS =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private final MainActivity host;
    private final ScrollView scrollView;
    private final LinearLayout messagesContainer;
    private final EditText input;
    private final Button sendButton;
    private final Spinner providerSpinner;
    private final Spinner modelSpinner;
    private final Button refreshModelsButton;
    private final TextView connectionLabel;
    private final TextView backgroundSupportLabel;
    private final Button conversationButton;
    private final TextView headerSubtitle;
    private LinearLayout selectorDetails;
    private TextView selectorSummary;
    private Button selectorToggle;
    private boolean selectorExpanded;

    private boolean bindingSelection;
    private String displayedModel;
    private boolean sending;
    private boolean loadingImages;
    private Button attachButton;
    private LinearLayout attachmentStrip;
    private final List<ImageAttachment> pendingAttachments = new ArrayList<>();
    private MessageBubble activeAssistantBubble;
    private ChatMessage displayedReply;
    private static final long STREAM_RENDER_INTERVAL_MS = 120L;
    private long lastStreamRenderAt;
    private boolean streamRenderScheduled;
    private final Runnable streamRenderTask = new Runnable() {
        @Override
        public void run() {
            streamRenderScheduled = false;
            lastStreamRenderAt = android.os.SystemClock.uptimeMillis();
            if (activeAssistantBubble == null) {
                return;
            }
            activeAssistantBubble.bindMessage(displayedReply);
            scrollToBottom();
        }
    };
    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (activeAssistantBubble != null && displayedReply != null && displayedReply.isPending()) {
                activeAssistantBubble.refreshProgress();
                postDelayed(this, 1000);
            }
        }
    };

    public ChatScreen(Context context, MainActivity host) {
        super(context);
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(UiKit.CANVAS);

        addView(buildHeader());
        addView(buildSelector(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(UiKit.dp(context, 12), UiKit.dp(context, 10),
                UiKit.dp(context, 12), UiKit.dp(context, 10));
        messagesContainer = UiKit.vertical(context);
        scrollView.addView(messagesContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        input = UiKit.input(context, "输入消息", true);
        input.setMaxLines(6);
        input.setMinLines(1);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setRawInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        sendButton = UiKit.button(context, "发送", true);
        sendButton.setOnClickListener(view -> {
            if (sending) {
                host.getChatSession().cancelReply();
            } else {
                sendMessage();
            }
        });

        addView(buildComposer(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        providerSpinner = findViewWithTag("provider_spinner");
        modelSpinner = findViewWithTag("model_spinner");
        refreshModelsButton = findViewWithTag("refresh_models");
        connectionLabel = findViewWithTag("connection_label");
        backgroundSupportLabel = findViewWithTag("background_support");
        conversationButton = findViewWithTag("conversation_button");
        headerSubtitle = findViewWithTag("header_subtitle");
        refreshProviders();
    }

    public void refreshProviders() {
        if (providerSpinner == null) {
            return;
        }
        List<ProviderProfile> providers = host.getProviders();
        List<String> providerNames = new ArrayList<>();
        for (ProviderProfile provider : providers) {
            providerNames.add(provider.getName());
        }

        bindingSelection = true;
        if (providerNames.isEmpty()) {
            UiKit.replaceSpinnerValues(providerSpinner, Collections.singletonList("未配置服务商"), "");
            UiKit.replaceSpinnerValues(modelSpinner, Collections.singletonList("未选择模型"), "");
            providerSpinner.setEnabled(false);
            modelSpinner.setEnabled(false);
            refreshModelsButton.setEnabled(false);
            connectionLabel.setText("先在设置中添加中转站和 API Key");
            input.setEnabled(false);
            sendButton.setEnabled(false);
            if (attachButton != null) {
                attachButton.setEnabled(false);
            }
        } else {
            ProviderProfile selected = host.getSelectedProvider();
            UiKit.replaceSpinnerValues(providerSpinner, providerNames,
                    selected == null ? "" : selected.getName());
            providerSpinner.setEnabled(true);
            connectionLabel.setText(selected == null ? "" : modeLabel(selected.getMode()));
            bindModelSpinner(selected);
            input.setEnabled(true);
            sendButton.setEnabled(true);
            if (attachButton != null) {
                attachButton.setEnabled(!sending && !loadingImages);
            }
            refreshModelsButton.setEnabled(selected != null && !selected.getBaseUrl().isEmpty());
        }
        bindingSelection = false;
        updateSelectorSummary();
        recordModelChange(host.getSelectedProvider());
        rebuildMessages();
        setSending(host.getChatSession().isSending());
        refreshBackgroundSupport();
    }

    public void refreshBackgroundSupport() {
        if (backgroundSupportLabel != null) {
            backgroundSupportLabel.setVisibility(
                    host.isBackgroundReplyRestricted() ? View.VISIBLE : View.GONE);
            updateSelectorVisibility();
        }
    }

    public void onProviderSelectionChanged() {
        refreshProviders();
    }

    private View buildHeader() {
        LinearLayout header = UiKit.horizontal(getContext());
        header.setPadding(UiKit.dp(getContext(), 18), UiKit.dp(getContext(), 9),
                UiKit.dp(getContext(), 18), UiKit.dp(getContext(), 9));
        header.setBackgroundColor(UiKit.SURFACE);
        header.setMinimumHeight(UiKit.dp(getContext(), 70));

        LinearLayout titles = UiKit.vertical(getContext());
        TextView title = UiKit.heading(getContext(), "RelayChat", 22);
        TextView subtitle = UiKit.text(getContext(), "", 1, UiKit.MUTED);
        subtitle.setTag("header_subtitle");
        subtitle.setVisibility(View.GONE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, titleParams);

        Button conversations = UiKit.button(getContext(), "会话", false);
        UiKit.setIcon(conversations, UiKit.Icon.USERS, getContext());
        conversations.setTag("conversation_button");
        conversations.setOnClickListener(view -> host.showConversations());
        header.addView(conversations);

        Button settings = UiKit.button(getContext(), "设置", false);
        UiKit.setIcon(settings, UiKit.Icon.GEAR, getContext());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsParams.leftMargin = UiKit.dp(getContext(), 12);
        settings.setOnClickListener(view -> host.showSettings());
        header.addView(settings, settingsParams);
        return header;
    }

    private View buildSelector() {
        LinearLayout selector = UiKit.vertical(getContext());
        selector.setPadding(UiKit.dp(getContext(), 18), UiKit.dp(getContext(), 2),
                UiKit.dp(getContext(), 18), UiKit.dp(getContext(), 2));
        selector.setBackgroundColor(UiKit.SURFACE);

        LinearLayout summaryRow = UiKit.horizontal(getContext());
        summaryRow.setMinimumHeight(UiKit.dp(getContext(), 40));
        selectorSummary = UiKit.text(getContext(), "未配置服务商 · 未选择模型", 13, UiKit.MUTED);
        selectorSummary.setSingleLine(true);
        selectorSummary.setEllipsize(TextUtils.TruncateAt.END);
        selectorSummary.setMaxWidth(UiKit.dp(getContext(), 260));
        summaryRow.addView(selectorSummary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        selectorToggle = UiKit.iconButton(getContext(), UiKit.Icon.TRIANGLE_DOWN, false);
        selectorToggle.setContentDescription("展开服务商和模型选择");
        selectorToggle.setBackground(null);
        selectorToggle.setMinWidth(0);
        selectorToggle.setMinimumWidth(0);
        selectorToggle.setMinHeight(0);
        selectorToggle.setMinimumHeight(0);
        selectorToggle.setPadding(UiKit.dp(getContext(), 2), 0,
                UiKit.dp(getContext(), 2), 0);
        summaryRow.addView(selectorToggle, new LinearLayout.LayoutParams(
                UiKit.dp(getContext(), 28), UiKit.dp(getContext(), 40)));
        selectorToggle.setOnClickListener(view -> {
            selectorExpanded = !selectorExpanded;
            updateSelectorVisibility();
        });
        selector.addView(summaryRow);

        selectorDetails = UiKit.vertical(getContext());
        selectorDetails.setVisibility(View.GONE);
        selector.addView(selectorDetails);

        LinearLayout row = UiKit.horizontal(getContext());
        row.setGravity(Gravity.TOP);

        LinearLayout providerColumn = UiKit.vertical(getContext());
        providerColumn.addView(label("服务商"));
        Spinner provider = UiKit.spinner(getContext(), Collections.singletonList(""));
        provider.setTag("provider_spinner");
        providerColumn.addView(provider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout modelColumn = UiKit.vertical(getContext());
        modelColumn.addView(label("模型"));
        Spinner model = UiKit.spinner(getContext(), Collections.singletonList(""));
        model.setTag("model_spinner");
        modelColumn.addView(model, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(providerColumn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.18f);
        modelParams.leftMargin = UiKit.dp(getContext(), 22);
        row.addView(modelColumn, modelParams);

        Button refresh = UiKit.button(getContext(), "刷新", false);
        UiKit.setIcon(refresh, UiKit.Icon.REFRESH, getContext());
        refresh.setTag("refresh_models");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshParams.leftMargin = UiKit.dp(getContext(), 20);
        refreshParams.topMargin = UiKit.dp(getContext(), 8);
        row.addView(refresh, refreshParams);
        selectorDetails.addView(row);

        TextView status = UiKit.text(getContext(), "", 11, UiKit.MUTED);
        status.setTag("connection_label");
        LinearLayout.LayoutParams statusParams = UiKit.matchWrap(UiKit.dp(getContext(), 5), 0);
        selectorDetails.addView(status, statusParams);

        TextView backgroundSupport = UiKit.text(getContext(),
                "后台回答可能受省电限制 · 点击允许后台运行", 12, UiKit.WARM);
        backgroundSupport.setTag("background_support");
        int supportPadding = UiKit.dp(getContext(), 8);
        backgroundSupport.setPadding(supportPadding, supportPadding, supportPadding, supportPadding);
        backgroundSupport.setOnClickListener(view -> host.allowBackgroundReplies());
        selectorDetails.addView(backgroundSupport, UiKit.matchWrap(UiKit.dp(getContext(), 4), 0));

        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bindingSelection || position < 0 || position >= host.getProviders().size()) {
                    return;
                }
                ProviderProfile provider = host.getProviders().get(position);
                ProviderProfile current = host.getSelectedProvider();
                if (current != null && current.getId().equals(provider.getId())) {
                    return;
                }
                host.selectProvider(provider.getId());
                bindingSelection = true;
                bindModelSpinner(provider);
                bindingSelection = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        model.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bindingSelection) {
                    return;
                }
                ProviderProfile provider = host.getSelectedProvider();
                if (provider == null || provider.getModels().isEmpty()
                        || position < 0 || position >= provider.getModels().size()) {
                    return;
                }
                String model = provider.getModels().get(position);
                if (model.equals(provider.getSelectedModel())) {
                    return;
                }
                provider.setSelectedModel(model);
                host.persistProviders();
                connectionLabel.setText(modeLabel(provider.getMode()) + " · " + provider.getSelectedModel());
                updateSelectorSummary();
                if (recordModelChange(provider)) {
                    rebuildMessages();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        refresh.setOnClickListener(view -> fetchModels());
        return selector;
    }

    private void updateSelectorVisibility() {
        if (selectorDetails == null) {
            return;
        }
        boolean showDetails = selectorExpanded || host.isBackgroundReplyRestricted();
        selectorDetails.setVisibility(showDetails ? View.VISIBLE : View.GONE);
        if (selectorToggle != null) {
            UiKit.setIcon(selectorToggle,
                    selectorExpanded ? UiKit.Icon.TRIANGLE_UP : UiKit.Icon.TRIANGLE_DOWN,
                    getContext());
            selectorToggle.setContentDescription(selectorExpanded
                    ? "收起服务商和模型选择" : "展开服务商和模型选择");
        }
    }

    private void updateSelectorSummary() {
        if (selectorSummary == null) {
            return;
        }
        ProviderProfile provider = host.getSelectedProvider();
        if (provider == null) {
            selectorSummary.setText("未配置服务商 · 未选择模型");
            return;
        }
        String model = provider.getSelectedModel().isEmpty()
                ? "未选择模型" : provider.getSelectedModel();
        selectorSummary.setText(provider.getName() + " · " + model);
    }

    private View buildComposer() {
        LinearLayout composer = UiKit.vertical(getContext());
        composer.setPadding(UiKit.dp(getContext(), 29), UiKit.dp(getContext(), 8),
                UiKit.dp(getContext(), 29), UiKit.dp(getContext(), 8));
        composer.setBackgroundColor(UiKit.SURFACE);
        composer.setElevation(UiKit.dp(getContext(), 2));

        attachmentStrip = UiKit.horizontal(getContext());
        attachmentStrip.setVisibility(View.GONE);
        composer.addView(attachmentStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = UiKit.horizontal(getContext());
        row.setGravity(Gravity.BOTTOM);

        attachButton = UiKit.iconButton(getContext(), UiKit.Icon.IMAGE, false);
        attachButton.setContentDescription("添加图片");
        attachButton.setMinWidth(UiKit.dp(getContext(), 56));
        attachButton.setMinHeight(UiKit.dp(getContext(), 48));
        attachButton.setOnClickListener(view -> host.pickImages());
        LinearLayout.LayoutParams attachParams = new LinearLayout.LayoutParams(
                UiKit.dp(getContext(), 56), UiKit.dp(getContext(), 48));
        attachParams.rightMargin = UiKit.dp(getContext(), 10);
        row.addView(attachButton, attachParams);

        input.setMinHeight(UiKit.dp(getContext(), 48));
        row.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sendParams.leftMargin = UiKit.dp(getContext(), 10);
        sendButton.setMinHeight(UiKit.dp(getContext(), 48));
        UiKit.setIcon(sendButton, UiKit.Icon.SEND, getContext());
        row.addView(sendButton, sendParams);
        composer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return composer;
    }

    private TextView label(String value) {
        TextView label = UiKit.text(getContext(), value, 11, UiKit.MUTED);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(UiKit.dp(getContext(), 3), 0, 0, UiKit.dp(getContext(), 3));
        return label;
    }

    private void bindModelSpinner(ProviderProfile provider) {
        if (provider == null) {
            UiKit.replaceSpinnerValues(modelSpinner, Collections.singletonList("未选择模型"), "");
            modelSpinner.setEnabled(false);
            return;
        }
        List<String> models = provider.getModels();
        if (models.isEmpty()) {
            UiKit.replaceSpinnerValues(modelSpinner, Collections.singletonList("暂无模型，请刷新"), "");
            modelSpinner.setEnabled(false);
        } else {
            UiKit.replaceSpinnerValues(modelSpinner, models, provider.getSelectedModel());
            modelSpinner.setEnabled(true);
        }
        connectionLabel.setText(modeLabel(provider.getMode())
                + (provider.getSelectedModel().isEmpty() ? "" : " · " + provider.getSelectedModel()));
    }

    private void fetchModels() {
        ProviderProfile provider = host.getSelectedProvider();
        if (provider == null) {
            host.showSettings();
            return;
        }
        if (provider.getBaseUrl().isEmpty()) {
            host.toast("请先填写 API 地址");
            return;
        }
        refreshModelsButton.setEnabled(false);
        refreshModelsButton.setText("获取中");
        host.getApiClient().fetchModels(provider, new com.relaychat.app.network.ApiClient.ModelCallback() {
            @Override
            public void onSuccess(List<String> models) {
                postToHost(() -> {
                    provider.setModels(models);
                    if (provider.getSelectedModel().isEmpty() && !models.isEmpty()) {
                        provider.setSelectedModel(models.get(0));
                    }
                    host.persistProviders();
                    refreshModelsButton.setText("刷新");
                    refreshModelsButton.setEnabled(true);
                    refreshProviders();
                    host.toast("已获取 " + models.size() + " 个模型");
                });
            }

            @Override
            public void onError(String message) {
                postToHost(() -> {
                    refreshModelsButton.setText("刷新");
                    refreshModelsButton.setEnabled(true);
                    host.toast(message);
                });
            }
        });
    }

    private void sendMessage() {
        if (host.getChatSession().isSending() || loadingImages) {
            return;
        }
        String text = input.getText().toString().trim();
        ProviderProfile provider = host.getSelectedProvider();
        if (provider == null) {
            host.showSettings();
            return;
        }
        if (text.isEmpty() && pendingAttachments.isEmpty()) {
            return;
        }
        if (provider.getSelectedModel().isEmpty()) {
            host.toast("请先选择或填写模型");
            return;
        }

        input.setText("");
        List<ImageAttachment> attachments = new ArrayList<>(pendingAttachments);
        pendingAttachments.clear();
        refreshAttachmentStrip();
        ChatMessage userMessage = new ChatMessage(ChatMessage.ROLE_USER, text, attachments);
        host.getChatSession().send(provider, userMessage);
        host.requestReplyNotifications();
    }

    public void onReplyChanged() {
        boolean running = host.getChatSession().isSending();
        ChatMessage reply = host.getChatSession().getReplyMessage();
        if (!running || reply != displayedReply || activeAssistantBubble == null) {
            rebuildMessages();
        } else {
            renderStreamingText();
        }
        scheduleProgressTick();
        if (sending != running) {
            setSending(running);
        }
    }

    public void pauseRendering() {
        cancelPendingRender();
        removeCallbacks(progressTick);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleProgressTick();
    }

    @Override
    protected void onDetachedFromWindow() {
        pauseRendering();
        super.onDetachedFromWindow();
    }

    private void scheduleProgressTick() {
        removeCallbacks(progressTick);
        if (isAttachedToWindow() && displayedReply != null && displayedReply.isPending()) {
            postDelayed(progressTick, 1000);
        }
    }

    private boolean recordModelChange(ProviderProfile provider) {
        String model = provider == null ? "" : provider.getSelectedModel();
        String previous = displayedModel;
        displayedModel = model;
        if (previous == null || model.isEmpty() || model.equals(previous)) {
            return false;
        }
        host.getHistory().add(new ChatMessage(ChatMessage.ROLE_NOTICE, "切换模型为 " + model));
        host.persistConversations();
        return true;
    }

    private void setSending(boolean value) {
        sending = value;
        providerSpinner.setEnabled(!value && !host.getProviders().isEmpty());
        modelSpinner.setEnabled(!value && host.getSelectedProvider() != null
                && !host.getSelectedProvider().getModels().isEmpty());
        refreshModelsButton.setEnabled(!value && host.getSelectedProvider() != null);
        input.setEnabled(!value && !host.getProviders().isEmpty());
        sendButton.setEnabled(value || !host.getProviders().isEmpty());
        conversationButton.setEnabled(!value);
        if (attachButton != null) {
            attachButton.setEnabled(!value && !loadingImages && !host.getProviders().isEmpty());
        }
        sendButton.setText(value ? "停止" : "发送");
        if (value) {
            sendButton.setBackground(UiKit.rounded(UiKit.WARM_SOFT, UiKit.dp(getContext(), 8),
                    UiKit.WARM, UiKit.dp(getContext(), 1)));
            sendButton.setTextColor(UiKit.WARM);
            UiKit.setIcon(sendButton, UiKit.Icon.SEND, getContext());
        } else {
            sendButton.setBackground(UiKit.rounded(UiKit.ACCENT, UiKit.dp(getContext(), 8),
                    UiKit.ACCENT, UiKit.dp(getContext(), 1)));
            sendButton.setTextColor(android.graphics.Color.WHITE);
            UiKit.setIcon(sendButton, UiKit.Icon.SEND, getContext());
            input.requestFocus();
        }
    }

    private void rebuildMessages() {
        cancelPendingRender();
        activeAssistantBubble = null;
        displayedReply = host.getChatSession().getReplyMessage();
        lastStreamRenderAt = 0L;
        messagesContainer.removeAllViews();
        headerSubtitle.setText(host.getActiveConversation().getTitle());
        if (host.getHistory().isEmpty()) {
            messagesContainer.addView(buildEmptyState());
            return;
        }
        for (ChatMessage message : host.getHistory()) {
            if (ChatMessage.ROLE_NOTICE.equals(message.getRole())) {
                addNotice(message.getContent());
            } else if (message.hasAttachments()) {
                addUserMessageBubble(message);
            } else {
                MessageBubble bubble = addBubble(message);
                if (message == displayedReply) {
                    activeAssistantBubble = bubble;
                }
            }
        }
        scheduleProgressTick();
        scrollToBottom();
    }

    private void addNotice(String text) {
        TextView notice = UiKit.text(getContext(), text, 12, UiKit.MUTED);
        notice.setGravity(Gravity.CENTER);
        int padding = UiKit.dp(getContext(), 8);
        notice.setPadding(padding, padding, padding, padding);
        messagesContainer.addView(notice, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View buildEmptyState() {
        LinearLayout panel = UiKit.vertical(getContext());
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(UiKit.dp(getContext(), 24), UiKit.dp(getContext(), 32),
                UiKit.dp(getContext(), 24), UiKit.dp(getContext(), 24));

        ProviderProfile provider = host.getSelectedProvider();
        TextView icon = UiKit.iconView(getContext(), UiKit.Icon.CHAT, UiKit.MUTED);
        panel.addView(icon, new LinearLayout.LayoutParams(
                UiKit.dp(getContext(), 64), UiKit.dp(getContext(), 64)));
        if (provider == null) {
            TextView title = UiKit.heading(getContext(), "尚未配置 API", 24);
            title.setGravity(Gravity.CENTER);
            TextView detail = UiKit.text(getContext(),
                    "添加兼容 OpenAI 接口的中转站后即可开始对话。", 15, UiKit.MUTED);
            detail.setGravity(Gravity.CENTER);
            Button add = UiKit.button(getContext(), "添加服务商", true);
            add.setOnClickListener(view -> host.showSettings());
            panel.addView(title, UiKit.matchWrap(UiKit.dp(getContext(), 12), 0));
            panel.addView(detail, UiKit.matchWrap(UiKit.dp(getContext(), 8), UiKit.dp(getContext(), 18)));
            panel.addView(add, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            TextView title = UiKit.heading(getContext(), "开始新的对话", 24);
            title.setGravity(Gravity.CENTER);
            String model = provider.getSelectedModel().isEmpty() ? "尚未选择模型" : provider.getSelectedModel();
            TextView detail = UiKit.text(getContext(), provider.getName() + " · " + model, 15, UiKit.MUTED);
            detail.setGravity(Gravity.CENTER);
            panel.addView(title, UiKit.matchWrap(UiKit.dp(getContext(), 12), 0));
            panel.addView(detail, UiKit.matchWrap(UiKit.dp(getContext(), 8), 0));
        }
        return panel;
    }

    private MessageBubble addBubble(ChatMessage message) {
        boolean user = ChatMessage.ROLE_USER.equals(message.getRole());
        MessageBubble bubble = new MessageBubble(this, user);
        bubble.bindMessage(message);
        addBubbleRow(bubble, user);
        return bubble;
    }

    private void addBubbleRow(View bubble, boolean user) {
        LinearLayout row = UiKit.horizontal(getContext());
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.addView(bubble, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = UiKit.dp(getContext(), 6);
        messagesContainer.addView(row, rowParams);
        scrollToBottom();
    }

    private void addUserMessageBubble(ChatMessage message) {
        List<ImageAttachment> attachments = message.getAttachments();
        if (!attachments.isEmpty()) {
            int available = Math.max(UiKit.dp(getContext(), 120),
                    maxBubbleWidth());
            int maxWidth = available;
            int maxHeight = UiKit.dp(getContext(), attachments.size() == 1 ? 360 : 132);
            int gap = UiKit.dp(getContext(), 6);
            int[] widths = new int[attachments.size()];
            int[] heights = new int[attachments.size()];
            List<Bitmap> bitmaps = new ArrayList<>();
            int total = 0;
            for (int index = 0; index < attachments.size(); index++) {
                Bitmap bitmap = thumbnailFor(attachments.get(index), maxHeight);
                bitmaps.add(bitmap);
                float aspect = bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0
                        ? bitmap.getWidth() / (float) bitmap.getHeight() : 1f;
                int width = Math.max(1, Math.round(maxHeight * aspect));
                int height = maxHeight;
                if (attachments.size() == 1 && width > maxWidth) {
                    width = maxWidth;
                    height = Math.max(1, Math.round(width / aspect));
                }
                widths[index] = width;
                heights[index] = height;
                total += width;
            }
            available = Math.max(UiKit.dp(getContext(), 120),
                    available - gap * (attachments.size() - 1));
            double factor = total > available ? available / (double) total : 1d;

            LinearLayout images = UiKit.horizontal(getContext());
            for (int index = 0; index < attachments.size(); index++) {
                ImageView preview = new ImageView(getContext());
                preview.setAdjustViewBounds(true);
                preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                preview.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(getContext(), 8),
                        UiKit.BORDER, UiKit.dp(getContext(), 1)));
                preview.setClipToOutline(true);
                preview.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                preview.setImageBitmap(bitmaps.get(index));
                ImageAttachment attachment = attachments.get(index);
                preview.setContentDescription("查看图片");
                preview.setOnClickListener(view -> ImageViewerDialog.show(getContext(), attachment));
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                        Math.max(1, (int) Math.round(widths[index] * factor)),
                        Math.max(1, (int) Math.round(heights[index] * factor)));
                if (index > 0) {
                    imageParams.leftMargin = gap;
                }
                images.addView(preview, imageParams);
            }
            addBubbleRow(images, true);
        }

        if (!message.getContent().trim().isEmpty()) {
            LinearLayout bubble = UiKit.vertical(getContext());
            bubble.setPadding(UiKit.dp(getContext(), 10), UiKit.dp(getContext(), 9),
                    UiKit.dp(getContext(), 10), UiKit.dp(getContext(), 9));
            bubble.setBackground(UiKit.rounded(UiKit.ACCENT, UiKit.dp(getContext(), 8),
                    UiKit.ACCENT, 0));
            MarkdownView text = buildBody(maxBubbleWidth() - UiKit.dp(getContext(), 20));
            text.setMarkdown(message.getContent(), MarkdownRenderer.Palette.user(
                    getResources().getDisplayMetrics().density));
            bubble.addView(text);
            addBubbleRow(bubble, true);
        }
    }

    private void refreshAttachmentStrip() {
        if (attachmentStrip == null) {
            return;
        }
        attachmentStrip.removeAllViews();
        if (pendingAttachments.isEmpty()) {
            attachmentStrip.setVisibility(View.GONE);
            return;
        }
        attachmentStrip.setVisibility(View.VISIBLE);
        for (ImageAttachment attachment : pendingAttachments) {
            attachmentStrip.addView(buildAttachmentChip(attachment));
        }
    }

    private View buildAttachmentChip(ImageAttachment attachment) {
        FrameLayout chip = new FrameLayout(getContext());
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                UiKit.dp(getContext(), 62), UiKit.dp(getContext(), 60));
        chipParams.bottomMargin = UiKit.dp(getContext(), 6);
        chipParams.rightMargin = UiKit.dp(getContext(), 4);
        chip.setLayoutParams(chipParams);

        ImageView preview = new ImageView(getContext());
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(UiKit.rounded(UiKit.FIELD, UiKit.dp(getContext(), 6),
                UiKit.BORDER, UiKit.dp(getContext(), 1)));
        preview.setClipToOutline(true);
        preview.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        preview.setImageBitmap(thumbnailFor(attachment, UiKit.dp(getContext(), 52)));
        preview.setContentDescription("点击编辑图片");
        preview.setOnClickListener(view -> ImageEditorDialog.show(getContext(), attachment,
                edited -> {
                    int index = pendingAttachments.indexOf(attachment);
                    if (index >= 0) {
                        pendingAttachments.set(index, edited);
                        refreshAttachmentStrip();
                    }
                }));
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(
                UiKit.dp(getContext(), 52), UiKit.dp(getContext(), 52));
        previewParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        chip.addView(preview, previewParams);

        TextView remove = UiKit.text(getContext(), "×", 13, android.graphics.Color.WHITE);
        remove.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        remove.setGravity(Gravity.CENTER);
        remove.setBackground(UiKit.rounded(UiKit.WARM, UiKit.dp(getContext(), 10),
                UiKit.WARM, 0));
        remove.setOnClickListener(view -> {
            pendingAttachments.remove(attachment);
            refreshAttachmentStrip();
        });
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                UiKit.dp(getContext(), 20), UiKit.dp(getContext(), 20));
        removeParams.gravity = Gravity.END | Gravity.TOP;
        chip.addView(remove, removeParams);
        return chip;
    }

    private Bitmap thumbnailFor(ImageAttachment attachment, int targetSize) {
        String key = attachment.getId() + "@" + targetSize;
        Bitmap cached = THUMBNAILS.get(key);
        if (cached != null) {
            return cached;
        }
        Bitmap bitmap = decodeBitmap(attachment.getData(), targetSize);
        if (bitmap != null) {
            THUMBNAILS.put(key, bitmap);
        }
        return bitmap;
    }

    private Bitmap decodeBitmap(byte[] data, int targetSize) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (largest / (sample * 2) >= Math.max(1, targetSize)) {
                sample *= 2;
            }
            options.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception | OutOfMemoryError error) {
            return null;
        }
    }

    public void importImages(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        int remaining = MAX_ATTACHMENTS - pendingAttachments.size();
        if (remaining <= 0) {
            host.toast("最多添加 " + MAX_ATTACHMENTS + " 张图片");
            return;
        }
        boolean trimmed = uris.size() > remaining;
        List<Uri> accepted = new ArrayList<>(uris.subList(0, Math.min(remaining, uris.size())));
        setLoadingImages(true);
        Context appContext = getContext().getApplicationContext();
        Thread worker = new Thread(() -> {
            List<ImageAttachment> loaded = new ArrayList<>();
            int failed = 0;
            for (Uri uri : accepted) {
                try {
                    loaded.add(ImagePicker.load(appContext.getContentResolver(), uri));
                } catch (Exception error) {
                    failed++;
                }
            }
            int failureCount = failed;
            postToHost(() -> {
                setLoadingImages(false);
                pendingAttachments.addAll(loaded);
                refreshAttachmentStrip();
                if (failureCount > 0) {
                    host.toast(failureCount + " 张图片读取失败");
                } else if (trimmed) {
                    host.toast("最多添加 " + MAX_ATTACHMENTS + " 张图片");
                }
            });
        }, "relaychat-image");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isSending() {
        return host.getChatSession().isSending();
    }

    public void clearPendingAttachments() {
        pendingAttachments.clear();
        refreshAttachmentStrip();
    }

    private void setLoadingImages(boolean value) {
        loadingImages = value;
        if (attachButton == null) {
            return;
        }
        attachButton.setText("");
        attachButton.setContentDescription(value ? "正在处理图片" : "添加图片");
        attachButton.setEnabled(!value && !sending && !host.getProviders().isEmpty());
    }

    private void renderStreamingText() {
        if (activeAssistantBubble == null) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastStreamRenderAt >= STREAM_RENDER_INTERVAL_MS) {
            lastStreamRenderAt = now;
            activeAssistantBubble.bindMessage(displayedReply);
            scrollToBottom();
            return;
        }
        if (!streamRenderScheduled) {
            streamRenderScheduled = true;
            activeAssistantBubble.postDelayed(streamRenderTask, STREAM_RENDER_INTERVAL_MS);
        }
    }

    private void cancelPendingRender() {
        streamRenderScheduled = false;
        if (activeAssistantBubble != null) {
            activeAssistantBubble.removeCallbacks(streamRenderTask);
        }
    }

    /** Reply status and optional provider reasoning sit above the independently rendered answer. */
    private static final class MessageBubble extends LinearLayout {
        private final MarkdownView body;
        private final boolean user;
        private final LinearLayout progressHeader;
        private final ProgressBar progressSpinner;
        private final TextView progressLabel;
        private final LinearLayout reasoningPanel;
        private final TextView reasoningText;
        private final TextView processingText;
        private final TextView errorText;
        private ChatMessage message;
        private boolean expanded;
        private String renderedContent;
        private String renderedReasoning;

        MessageBubble(ChatScreen screen, boolean user) {
            super(screen.getContext());
            this.user = user;
            setOrientation(VERTICAL);
            int horizontal = UiKit.dp(getContext(), 13);
            int contentWidth = screen.maxBubbleWidth() - horizontal * 2;
            setPadding(horizontal, UiKit.dp(getContext(), 10), horizontal,
                    UiKit.dp(getContext(), 10));
            progressHeader = UiKit.horizontal(getContext());
            progressHeader.setGravity(Gravity.TOP);
            progressHeader.setMinimumHeight(UiKit.dp(getContext(), 36));
            progressSpinner = new ProgressBar(getContext());
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                    UiKit.dp(getContext(), 16), UiKit.dp(getContext(), 16));
            spinnerParams.rightMargin = UiKit.dp(getContext(), 8);
            spinnerParams.topMargin = UiKit.dp(getContext(), 9);
            progressHeader.addView(progressSpinner, spinnerParams);
            progressLabel = UiKit.text(getContext(), "", 13, UiKit.MUTED);
            progressLabel.setPadding(0, UiKit.dp(getContext(), 6), 0, UiKit.dp(getContext(), 6));
            progressLabel.setMaxWidth(contentWidth - UiKit.dp(getContext(), 24));
            progressHeader.addView(progressLabel);
            progressHeader.setOnClickListener(view -> {
                if (message != null && !user) {
                    expanded = !expanded;
                    refreshProgress();
                }
            });
            addView(progressHeader);

            processingText = UiKit.text(getContext(), "", 12, UiKit.MUTED);
            processingText.setMaxWidth(contentWidth);
            processingText.setLineSpacing(UiKit.dp(getContext(), 3), 1f);
            processingText.setPadding(0, 0, 0, UiKit.dp(getContext(), 10));
            addView(processingText);

            reasoningPanel = UiKit.horizontal(getContext());
            reasoningPanel.setGravity(Gravity.TOP);
            View rule = new View(getContext());
            rule.setBackgroundColor(UiKit.BORDER);
            reasoningPanel.addView(rule, new LinearLayout.LayoutParams(
                    UiKit.dp(getContext(), 2), ViewGroup.LayoutParams.MATCH_PARENT));
            reasoningText = UiKit.text(getContext(), "", 14, UiKit.MUTED);
            reasoningText.setTextIsSelectable(true);
            reasoningText.setLineSpacing(UiKit.dp(getContext(), 3), 1f);
            reasoningText.setMaxWidth(contentWidth - UiKit.dp(getContext(), 12));
            LinearLayout.LayoutParams reasoningParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            reasoningParams.leftMargin = UiKit.dp(getContext(), 10);
            reasoningPanel.addView(reasoningText, reasoningParams);
            LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            panelParams.bottomMargin = UiKit.dp(getContext(), 10);
            addView(reasoningPanel, panelParams);

            body = screen.buildBody(contentWidth);
            addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            errorText = UiKit.text(getContext(), "", 13, UiKit.WARM);
            errorText.setMaxWidth(contentWidth);
            LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            errorParams.topMargin = UiKit.dp(getContext(), 6);
            addView(errorText, errorParams);
            applyBackground();
        }

        void bindMessage(ChatMessage value) {
            if (value == null) {
                return;
            }
            if (message != value) {
                expanded = value.isPending();
            }
            message = value;
            String content = value.getContent();
            if (!content.equals(renderedContent)) {
                renderedContent = content;
                body.setMarkdown(content, palette());
            }
            body.setVisibility(content.isEmpty() ? View.GONE : View.VISIBLE);
            String reasoning = value.getReasoning();
            if (!reasoning.equals(renderedReasoning)) {
                renderedReasoning = reasoning;
                reasoningText.setText(reasoning);
            }
            errorText.setText(value.getError());
            errorText.setVisibility(value.getError().isEmpty() ? View.GONE : View.VISIBLE);
            refreshProgress();
        }

        void refreshProgress() {
            if (message == null) {
                return;
            }
            boolean hasReasoning = !message.getReasoning().isEmpty();
            boolean visible = !user && (message.isPending() || !message.getStage().isEmpty()
                    || hasReasoning);
            progressHeader.setVisibility(visible ? View.VISIBLE : View.GONE);
            progressSpinner.setVisibility(message.isPending() ? View.VISIBLE : View.GONE);
            reasoningPanel.setVisibility(visible && hasReasoning && expanded ? View.VISIBLE : View.GONE);
            processingText.setVisibility(visible && expanded ? View.VISIBLE : View.GONE);
            if (visible && expanded) {
                processingText.setText(processingDetails());
            }
            progressHeader.setClickable(visible);
            String label = stageLabel(message.getStage());
            if (hasReasoning && !ChatMessage.STAGE_THINKING.equals(message.getStage())) {
                label = "已思考（用时 " + seconds(message.getReasoningDurationMs()) + " 秒）"
                        + (message.isPending() ? "\n" : " · ") + label;
            }
            label += "（" + (hasReasoning && !ChatMessage.STAGE_THINKING.equals(message.getStage())
                    ? "总用时 " : "用时 ") + seconds(ChatMessage.STAGE_THINKING.equals(message.getStage())
                    ? message.getReasoningDurationMs() : message.getProcessingDurationMs()) + " 秒）";
            if (message.isPending() && !message.getContent().isEmpty()) {
                label += "\n已生成 " + message.getContent().codePointCount(
                        0, message.getContent().length()) + " 个字符";
            }
            label += expanded ? "\n处理过程  ▾" : "\n查看处理过程  ▸";
            progressLabel.setText(label);
            progressHeader.setContentDescription(visible ? label
                    + (expanded ? "，点击收起处理过程" : "，点击展开处理过程") : null);
        }

        private String processingDetails() {
            StringBuilder details = new StringBuilder();
            List<ChatMessage.ProcessingStep> steps = message.getProcessingSteps();
            long total = message.getProcessingDurationMs();
            if (steps.size() <= 1 && !message.isPending()) {
                details.append("此回复未记录详细阶段；总用时 ").append(seconds(total)).append(" 秒。\n");
            } else {
                for (int index = 0; index < steps.size(); index++) {
                    ChatMessage.ProcessingStep step = steps.get(index);
                    boolean current = index == steps.size() - 1;
                    long end = current ? total : steps.get(index + 1).getStartedMs();
                    details.append(current && message.isPending() ? "● " : "✓ ")
                            .append(stageLabel(step.getStage()));
                    if (!current || message.isPending()) {
                        details.append(" · ").append(duration(Math.max(0, end - step.getStartedMs())));
                    }
                    details.append('\n');
                }
            }
            if (!message.getContent().isEmpty()) {
                details.append("已收到正文 ").append(message.getContent().codePointCount(
                        0, message.getContent().length())).append(" 个字符\n");
            }
            if (!message.getReasoning().isEmpty()) {
                details.append("接口返回的思考内容：");
            } else if (message.isPending()) {
                details.append(ChatMessage.STAGE_THINKING.equals(message.getStage())
                        ? "模型正在思考，尚未返回可显示的思考文本。"
                        : "等待接口返回内容；下方会实时显示返回的思考文本。");
            } else {
                details.append("接口未返回可显示的思考文本；以上为本次请求的处理记录。");
            }
            return details.toString();
        }

        private static String duration(long milliseconds) {
            return milliseconds < 1000 ? milliseconds + " 毫秒"
                    : String.format(java.util.Locale.ROOT, "%.1f 秒", milliseconds / 1000.0);
        }

        private static long seconds(long milliseconds) {
            return milliseconds / 1000;
        }

        private static String stageLabel(String stage) {
            switch (stage) {
                case ChatMessage.STAGE_PREPARING: return "准备请求";
                case ChatMessage.STAGE_CONNECTING: return "正在连接";
                case ChatMessage.STAGE_WAITING: return "等待模型响应";
                case ChatMessage.STAGE_THINKING: return "正在思考";
                case ChatMessage.STAGE_ANSWERING: return "正在生成回答";
                case ChatMessage.STAGE_FALLBACK: return "正在等待完整回答";
                case ChatMessage.STAGE_COMPLETED: return "回答完成";
                case ChatMessage.STAGE_STOPPED: return "已停止";
                case ChatMessage.STAGE_FAILED: return "回答失败";
                case ChatMessage.STAGE_INTERRUPTED: return "回答已中断";
                default: return "正在处理";
            }
        }

        private MarkdownRenderer.Palette palette() {
            float density = getResources().getDisplayMetrics().density;
            return user ? MarkdownRenderer.Palette.user(density)
                    : MarkdownRenderer.Palette.assistant(density);
        }

        private void applyBackground() {
            int radius = UiKit.dp(getContext(), 8);
            if (user) {
                setBackground(UiKit.rounded(UiKit.ACCENT, radius, UiKit.ACCENT, 0));
            } else {
                setBackground(UiKit.rounded(UiKit.SURFACE, radius, UiKit.BORDER,
                        UiKit.dp(getContext(), 1)));
            }
        }
    }

    private MarkdownView buildBody(int widthPx) {
        MarkdownView body = new MarkdownView(getContext());
        body.setContentWidth(widthPx);
        body.setTableOpener(table -> TableViewerDialog.show(getContext(), table));
        return body;
    }

    private int maxBubbleWidth() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.min(screenWidth - UiKit.dp(getContext(), 72), UiKit.dp(getContext(), 620));
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
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

    private String modeLabel(String mode) {
        return ProviderProfile.MODE_RESPONSES.equals(mode)
                ? "Responses API" : "Chat Completions";
    }
}
