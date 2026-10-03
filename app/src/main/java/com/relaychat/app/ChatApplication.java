package com.relaychat.app;

import android.app.Application;

import com.relaychat.app.data.ChatSession;

public final class ChatApplication extends Application {
    private ChatSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        session = new ChatSession(this);
    }

    public ChatSession getSession() {
        return session;
    }
}
