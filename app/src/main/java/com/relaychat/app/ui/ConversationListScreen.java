package com.relaychat.app.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.relaychat.app.MainActivity;
import com.relaychat.app.model.Conversation;

public final class ConversationListScreen extends LinearLayout {
    private final MainActivity host;
    private final LinearLayout conversationList;
    private Dialog actionsDialog;

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
            if (!conversation.hasMessages()) {
                continue;
            }
            boolean active = conversation.getId().equals(activeId);
            LinearLayout card = buildConversationCard(conversation, active);
            card.setOnClickListener(view -> host.selectConversation(conversation.getId()));
            card.setOnLongClickListener(view -> {
                showActions(conversation, active, view);
                return true;
            });
            card.setContentDescription(conversation.getTitle() + "，" + conversation.getSummary()
                    + (active ? "，当前对话" : ""));
            conversationList.addView(card, UiKit.matchWrap(0, 10));
        }

        TextView note = UiKit.text(getContext(),
                "对话保存在本机，卸载应用后才会清空。", 12, UiKit.MUTED);
        note.setGravity(Gravity.CENTER);
        conversationList.addView(note, UiKit.matchWrap(UiKit.dp(getContext(), 8), 0));
    }

    private LinearLayout buildConversationCard(Conversation conversation, boolean active) {
        Context context = getContext();
        LinearLayout card = UiKit.card(context);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x184F89E2),
                UiKit.rounded(UiKit.SURFACE, UiKit.dp(context, 8),
                        active ? UiKit.ACCENT : UiKit.BORDER, UiKit.dp(context, active ? 2 : 1)),
                UiKit.rounded(Color.WHITE, UiKit.dp(context, 8), 0, 0)));
        LinearLayout titleRow = UiKit.horizontal(context);
        TextView name = UiKit.heading(context, conversation.getTitle(), 17);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (active) {
            TextView badge = UiKit.text(context, "当前", 11, UiKit.ACCENT);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setPadding(UiKit.dp(context, 8), UiKit.dp(context, 3),
                    UiKit.dp(context, 8), UiKit.dp(context, 3));
            badge.setBackground(UiKit.rounded(UiKit.CANVAS, UiKit.dp(context, 6),
                    UiKit.ACCENT, UiKit.dp(context, 1)));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.leftMargin = UiKit.dp(context, 12);
            titleRow.addView(badge, badgeParams);
        }
        card.addView(titleRow);
        TextView summary = UiKit.text(context, conversation.getSummary(), 12, UiKit.MUTED);
        card.addView(summary, UiKit.matchWrap(UiKit.dp(context, 5), 0));
        return card;
    }

    private void showActions(Conversation conversation, boolean active, View anchor) {
        dismissActions();
        Context context = getContext();
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = UiKit.vertical(context);
        content.addView(buildConversationCard(conversation, active), UiKit.matchWrap(0,
                UiKit.dp(context, 10)));

        LinearLayout menu = UiKit.vertical(context);
        menu.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(context, 8), 0, 0));
        menu.setClipToOutline(true);
        menu.addView(actionRow(active ? "当前对话" : "打开", UiKit.Icon.CHAT,
                UiKit.INK, !active, dialog,
                () -> host.selectConversation(conversation.getId())));
        menu.addView(UiKit.divider(context));
        menu.addView(actionRow("重命名", UiKit.Icon.PENCIL, UiKit.INK, true,
                dialog, () -> renameConversation(conversation)));
        menu.addView(UiKit.divider(context));
        menu.addView(actionRow("删除", UiKit.Icon.TRASH, UiKit.DANGER, true,
                dialog, () -> confirmDelete(conversation)));
        content.addView(menu);

        // Keep the preview near the pressed row, and the whole menu inside the visible window.
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        int margin = UiKit.dp(context, 16);
        int width = frame.width() - margin * 2;
        int maxHeight = frame.height() - margin * 2;
        content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int height = Math.min(content.getMeasuredHeight(), maxHeight);
        int[] position = new int[2];
        anchor.getLocationOnScreen(position);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.width = width;
            params.height = height;
            params.x = frame.left + margin;
            params.y = Math.max(margin,
                    Math.min(position[1] - frame.top, frame.height() - margin - height));
            params.dimAmount = 0.18f;
            window.setAttributes(params);
        }
        actionsDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (actionsDialog == dialog) {
                actionsDialog = null;
            }
        });
        dialog.show();
    }

    private View actionRow(String label, UiKit.Icon icon, int color, boolean enabled,
                           Dialog dialog, Runnable action) {
        Context context = getContext();
        LinearLayout row = UiKit.horizontal(context);
        row.setMinimumHeight(UiKit.dp(context, 56));
        row.setPadding(UiKit.dp(context, 20), UiKit.dp(context, 14),
                UiKit.dp(context, 20), UiKit.dp(context, 14));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x184F89E2),
                null, UiKit.rounded(Color.WHITE, 0, 0, 0)));
        TextView text = UiKit.text(context, label, 17, color);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView image = UiKit.iconView(context, icon, color);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                UiKit.dp(context, 22), UiKit.dp(context, 22));
        imageParams.leftMargin = UiKit.dp(context, 16);
        row.addView(image, imageParams);
        row.setContentDescription(label);
        row.setOnClickListener(view -> {
            dialog.dismiss();
            action.run();
        });
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.45f);
        return row;
    }

    private void dismissActions() {
        if (actionsDialog != null) {
            actionsDialog.dismiss();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissActions();
        super.onDetachedFromWindow();
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
