package com.relaychat.app;

import android.app.Application;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.relaychat.app.data.ChatSession;

public final class ChatApplication extends Application {
    private ChatSession session;

    @Override
    public void onCreate() {
        synchronized (Python.class) {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }
        }
        super.onCreate();
        session = new ChatSession(this);
    }

    public ChatSession getSession() {
        return session;
    }
}
