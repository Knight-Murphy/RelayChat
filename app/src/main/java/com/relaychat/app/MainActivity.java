package com.relaychat.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.relaychat.app.data.ChatSession;
import com.relaychat.app.data.ProviderStore;
import com.relaychat.app.data.SecretStore;
import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.Conversation;
import com.relaychat.app.model.ProviderProfile;
import com.relaychat.app.network.ApiClient;
import com.relaychat.app.ui.ChatScreen;
import com.relaychat.app.ui.ConversationListScreen;
import com.relaychat.app.ui.ProviderEditorScreen;
import com.relaychat.app.ui.ProviderSettingsScreen;
import com.relaychat.app.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    private static final int REQUEST_PICK_IMAGES = 1001;

    private enum Screen {
        CHAT,
        SETTINGS,
        EDITOR,
        CONVERSATIONS
    }

    private final List<ProviderProfile> providers = new ArrayList<>();
    private List<Conversation> conversations;

    private FrameLayout root;
    private ProviderStore providerStore;
    private ApiClient apiClient;
    private ChatSession session;
    private ChatScreen chatScreen;
    private Screen currentScreen = Screen.CHAT;
    private boolean alive;
    private final ChatSession.Listener replyListener = () -> {
        if (chatScreen != null && isAlive()) {
            chatScreen.onReplyChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        alive = true;
        configureWindow();

        providerStore = new ProviderStore(this, new SecretStore());
        providers.addAll(providerStore.loadProviders());
        apiClient = new ApiClient();
        session = ((ChatApplication) getApplication()).getSession();
        conversations = session.getConversations();

        root = new FrameLayout(this);
        root.setBackgroundColor(UiKit.CANVAS);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                view.setPadding(
                        windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(),
                        windowInsets.getSystemWindowInsetBottom());
                return windowInsets;
            });
        }
        setContentView(root);
        showChat();
        openNotificationConversation(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openNotificationConversation(intent);
    }

    private void openNotificationConversation(Intent intent) {
        String conversationId = intent == null ? null : intent.getStringExtra(EXTRA_CONVERSATION_ID);
        if (conversationId != null) {
            selectConversation(conversationId);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentScreen == Screen.EDITOR) {
            showSettings();
        } else if (currentScreen == Screen.SETTINGS || currentScreen == Screen.CONVERSATIONS) {
            showChat();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGES || resultCode != RESULT_OK || data == null) {
            return;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int index = 0; index < clip.getItemCount(); index++) {
                Uri uri = clip.getItemAt(index).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (!uris.isEmpty() && chatScreen != null && currentScreen == Screen.CHAT) {
            chatScreen.importImages(uris);
        }
    }

    public void pickImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQUEST_PICK_IMAGES);
        } catch (ActivityNotFoundException missing) {
            try {
                startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_PICK_IMAGES);
            } catch (Exception error) {
                toast("没有可用的图片选择器");
            }
        } catch (Exception error) {
            toast("没有可用的图片选择器");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        session.addListener(replyListener);
        if (chatScreen != null) {
            chatScreen.refreshProviders();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatScreen != null) {
            chatScreen.refreshBackgroundSupport();
        }
    }

    @Override
    protected void onStop() {
        session.removeListener(replyListener);
        if (chatScreen != null) {
            chatScreen.pauseRendering();
        }
        persistConversations();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        session.removeListener(replyListener);
        if (chatScreen != null) {
            chatScreen.pauseRendering();
        }
        apiClient.shutdown();
        super.onDestroy();
    }

    public boolean isAlive() {
        return alive && !isFinishing();
    }

    public List<ProviderProfile> getProviders() {
        return providers;
    }

    public List<ChatMessage> getHistory() {
        return getActiveConversation().getMessages();
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    public String getActiveConversationId() {
        return session.getActiveId();
    }

    public Conversation getActiveConversation() {
        return session.getActiveConversation();
    }

    public boolean newConversation() {
        if (session.isSending()) {
            toast("回复生成中，请先停止后再新建对话");
            return false;
        }
        if (!getActiveConversation().getMessages().isEmpty()) {
            createConversation();
        }
        if (chatScreen != null) {
            chatScreen.clearPendingAttachments();
        }
        persistConversations();
        showChat();
        return true;
    }

    public boolean selectConversation(String conversationId) {
        Conversation conversation = findConversation(conversationId);
        if (conversation == null) {
            return false;
        }
        if (conversation.getId().equals(session.getActiveId())) {
            showChat();
            return true;
        }
        if (session.isSending()) {
            toast("回复生成中，请先停止后再切换对话");
            return false;
        }
        conversations.remove(conversation);
        conversations.add(0, conversation);
        session.setActiveId(conversation.getId());
        if (chatScreen != null) {
            chatScreen.clearPendingAttachments();
        }
        persistConversations();
        showChat();
        return true;
    }

    public boolean deleteConversation(String conversationId) {
        Conversation conversation = findConversation(conversationId);
        if (conversation == null) {
            return false;
        }
        boolean active = conversation.getId().equals(session.getActiveId());
        if (active && session.isSending()) {
            toast("回复生成中，请先停止后再删除对话");
            return false;
        }
        conversations.remove(conversation);
        if (conversations.isEmpty()) {
            createConversation();
            if (chatScreen != null) {
                chatScreen.clearPendingAttachments();
            }
        } else if (active) {
            session.setActiveId(conversations.get(0).getId());
            if (chatScreen != null) {
                chatScreen.clearPendingAttachments();
            }
        }
        persistConversations();
        return true;
    }

    private Conversation findConversation(String conversationId) {
        for (Conversation conversation : conversations) {
            if (conversation.getId().equals(conversationId)) {
                return conversation;
            }
        }
        return null;
    }

    public boolean renameConversation(String conversationId, String title) {
        Conversation conversation = findConversation(conversationId);
        if (conversation == null) {
            return false;
        }
        conversation.setTitle(title);
        persistConversations();
        return true;
    }

    private Conversation createConversation() {
        return session.createConversation();
    }

    public void persistConversations() {
        session.persist();
    }

    public ChatSession getChatSession() {
        return session;
    }

    public void requestReplyNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("notifications_requested", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notifications_requested", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
            return;
        }
        suggestBackgroundReplySupport();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1002 && isAlive()) {
            suggestBackgroundReplySupport();
        }
    }

    public boolean isBackgroundReplyRestricted() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        boolean optimized = power != null && !power.isIgnoringBatteryOptimizations(getPackageName());
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        boolean restricted = Build.VERSION.SDK_INT >= 28
                && manager != null && manager.isBackgroundRestricted();
        return optimized || restricted;
    }

    private void suggestBackgroundReplySupport() {
        if (!isAlive() || !isBackgroundReplyRestricted()
                || getPreferences(MODE_PRIVATE).getBoolean("background_support_suggested", false)) {
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("background_support_suggested", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("允许后台回答")
                .setMessage("为避免退出或锁屏后回答超时，请允许后台运行。"
                        + "如果系统中还有电池设置，请将 RelayChat 设为“不受限制”或“无限制”。")
                .setPositiveButton("去设置", (dialog, which) -> allowBackgroundReplies())
                .setNegativeButton("暂不", null)
                .show();
    }

    public void allowBackgroundReplies() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null && !power.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + getPackageName())));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Some devices expose only their own app battery settings.
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
            toast("请允许后台运行，并将电池设置为“不受限制”或“无限制”");
        } catch (ActivityNotFoundException | SecurityException ignored) {
            toast("请在系统设置中允许 RelayChat 后台运行，并关闭它的省电限制");
        }
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public ProviderProfile getSelectedProvider() {
        String selectedId = providerStore.getSelectedProviderId();
        for (ProviderProfile provider : providers) {
            if (provider.getId().equals(selectedId)) {
                return provider;
            }
        }
        return providers.isEmpty() ? null : providers.get(0);
    }

    public void selectProvider(String providerId) {
        providerStore.setSelectedProviderId(providerId);
        if (chatScreen != null) {
            chatScreen.onProviderSelectionChanged();
        }
    }

    public void saveProvider(ProviderProfile provider) {
        boolean replaced = false;
        for (int index = 0; index < providers.size(); index++) {
            if (providers.get(index).getId().equals(provider.getId())) {
                providers.set(index, provider);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            providers.add(provider);
        }
        providerStore.setSelectedProviderId(provider.getId());
        persistProviders();
    }

    public void deleteProvider(String providerId) {
        for (int index = providers.size() - 1; index >= 0; index--) {
            if (providers.get(index).getId().equals(providerId)) {
                providers.remove(index);
            }
        }
        if (providerId.equals(providerStore.getSelectedProviderId())) {
            providerStore.setSelectedProviderId(providers.isEmpty() ? "" : providers.get(0).getId());
        }
        persistProviders();
        if (chatScreen != null) {
            chatScreen.refreshProviders();
        }
    }

    public void persistProviders() {
        providerStore.saveProviders(providers);
    }

    public void showChat() {
        currentScreen = Screen.CHAT;
        if (chatScreen == null) {
            chatScreen = new ChatScreen(this, this);
        }
        chatScreen.refreshProviders();
        setRootView(chatScreen);
    }

    public void showSettings() {
        currentScreen = Screen.SETTINGS;
        setRootView(new ProviderSettingsScreen(this, this));
    }

    public void showConversations() {
        currentScreen = Screen.CONVERSATIONS;
        setRootView(new ConversationListScreen(this, this));
    }

    public void showProviderEditor(ProviderProfile provider) {
        currentScreen = Screen.EDITOR;
        setRootView(new ProviderEditorScreen(this, this, provider));
    }

    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setRootView(View view) {
        root.removeAllViews();
        root.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(UiKit.CANVAS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }
}
