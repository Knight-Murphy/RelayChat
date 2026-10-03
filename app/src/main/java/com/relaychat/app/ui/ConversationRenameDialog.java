package com.relaychat.app.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.relaychat.app.model.Conversation;

/** Names a conversation; shared by the conversation list and the chat header. */
public final class ConversationRenameDialog {
    /** Receives the entered name, which is empty when the automatic title should return. */
    public interface OnRename {
        void onRename(String title);
    }

    private ConversationRenameDialog() {
    }

    public static void show(Context context, Conversation conversation, OnRename callback) {
        LinearLayout content = UiKit.vertical(context);
        content.setPadding(UiKit.dp(context, 20), UiKit.dp(context, 4),
                UiKit.dp(context, 20), UiKit.dp(context, 4));

        EditText input = UiKit.input(context, "输入对话名称，留空则自动命名", false);
        input.setText(conversation.getCustomTitle());
        input.setSelection(input.getText().length());
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (conversation.getCustomTitle().isEmpty()) {
            TextView current = UiKit.text(context,
                    "当前自动命名：“" + conversation.getTitle() + "”", 12, UiKit.MUTED);
            content.addView(current, UiKit.matchWrap(UiKit.dp(context, 8), 0));
        }

        new AlertDialog.Builder(context)
                .setTitle("重命名对话")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存",
                        (dialog, which) -> callback.onRename(input.getText().toString()))
                .show();
    }
}
