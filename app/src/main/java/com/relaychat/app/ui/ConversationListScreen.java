package com.relaychat.app.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.relaychat.app.MainActivity;
import com.relaychat.app.model.Conversation;

public final class ConversationListScreen extends LinearLayout {
    private final MainActivity host;
    private final LinearLayout conversationList;

    public ConversationListScreen(Context context, MainActivity host) {
        super(context);
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(UiKit.CANVAS);
        addView(buildHeader());

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(UiKit.dp(context, 12), UiKit.dp(context, 12),
                UiKit.dp(context, 12), UiKit.dp(context, 20));

        conversationList = UiKit.vertical(context);
        scrollView.addView(conversationList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rebuild();
    }

    private View buildHeader() {
        LinearLayout header = UiKit.horizontal(getContext());
        header.setPadding(UiKit.dp(getContext(), 10), UiKit.dp(getContext(), 8),
                UiKit.dp(getContext(), 12), UiKit.dp(getContext(), 8));
        header.setBackgroundColor(UiKit.SURFACE);
        header.setElevation(UiKit.dp(getContext(), 2));

        Button back = UiKit.compactButton(getContext(), "返回", false);
        back.setOnClickListener(view -> host.showChat());
        header.addView(back);

        TextView title = UiKit.heading(getContext(), "对话列表", 18);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = UiKit.dp(getContext(), 12);
        header.addView(title, titleParams);

        Button create = UiKit.compactButton(getContext(), "新建", true);
        create.setOnClickListener(view -> host.newConversation());
        header.addView(create);
        return header;
    }

    private void rebuild() {
        conversationList.removeAllViews();

        TextView sectionTitle = UiKit.heading(getContext(), "全部对话", 15);
        conversationList.addView(sectionTitle, UiKit.matchWrap(2, 10));

        String activeId = host.getActiveConversationId();
        for (Conversation conversation : host.getConversations()) {
            boolean active = conversation.getId().equals(activeId);
            LinearLayout card = UiKit.card(getContext());
            if (active) {
                card.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(getContext(), 8),
                        UiKit.ACCENT, UiKit.dp(getContext(), 2)));
            }

            LinearLayout titleRow = UiKit.horizontal(getContext());
            TextView name = UiKit.heading(getContext(), conversation.getTitle(), 17);
            titleRow.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (active) {
                TextView badge = UiKit.text(getContext(), "当前", 11, UiKit.ACCENT);
                badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                badge.setPadding(UiKit.dp(getContext(), 8), UiKit.dp(getContext(), 3),
                        UiKit.dp(getContext(), 8), UiKit.dp(getContext(), 3));
                badge.setBackground(UiKit.rounded(UiKit.CANVAS, UiKit.dp(getContext(), 6),
                        UiKit.ACCENT, UiKit.dp(getContext(), 1)));
                titleRow.addView(badge);
            }
            card.addView(titleRow);

            TextView summary = UiKit.text(getContext(), conversation.getSummary(), 12, UiKit.MUTED);
            card.addView(summary, UiKit.matchWrap(UiKit.dp(getContext(), 5), UiKit.dp(getContext(), 12)));

            LinearLayout actions = UiKit.horizontal(getContext());
            Button open = UiKit.compactButton(getContext(), active ? "当前对话" : "打开", !active);
            open.setEnabled(!active);
            open.setOnClickListener(view -> host.selectConversation(conversation.getId()));
            actions.addView(open);

            Button rename = UiKit.compactButton(getContext(), "重命名", false);
            LinearLayout.LayoutParams renameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            renameParams.leftMargin = UiKit.dp(getContext(), 8);
            rename.setOnClickListener(view -> renameConversation(conversation));
            actions.addView(rename, renameParams);

            Button delete = UiKit.compactButton(getContext(), "删除", false);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteParams.leftMargin = UiKit.dp(getContext(), 8);
            delete.setTextColor(UiKit.DANGER);
            delete.setOnClickListener(view -> confirmDelete(conversation));
            actions.addView(delete, deleteParams);

            card.addView(actions);
            conversationList.addView(card, UiKit.matchWrap(0, 10));
        }

        TextView note = UiKit.text(getContext(),
                "对话保存在本机，卸载应用后才会清空。", 12, UiKit.MUTED);
        note.setGravity(Gravity.CENTER);
        conversationList.addView(note, UiKit.matchWrap(UiKit.dp(getContext(), 8), 0));
    }

    private void confirmDelete(Conversation conversation) {
        new AlertDialog.Builder(getContext())
                .setTitle("删除对话")
                .setMessage("确定删除“" + conversation.getTitle() + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    host.deleteConversation(conversation.getId());
                    host.showConversations();
                })
                .show();
    }

    private void renameConversation(Conversation conversation) {
        ConversationRenameDialog.show(getContext(), conversation, title -> {
            host.renameConversation(conversation.getId(), title);
            rebuild();
        });
    }
}
