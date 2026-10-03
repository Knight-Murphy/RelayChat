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
import com.relaychat.app.model.ProviderProfile;

import java.util.List;

public final class ProviderSettingsScreen extends LinearLayout {
    private final MainActivity host;
    private final LinearLayout providerList;

    public ProviderSettingsScreen(Context context, MainActivity host) {
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

        providerList = UiKit.vertical(context);
        scrollView.addView(providerList, new ScrollView.LayoutParams(
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

        TextView title = UiKit.heading(getContext(), "服务商设置", 18);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = UiKit.dp(getContext(), 12);
        header.addView(title, titleParams);

        Button add = UiKit.compactButton(getContext(), "新增", true);
        add.setOnClickListener(view -> host.showProviderEditor(null));
        header.addView(add);
        return header;
    }

    private void rebuild() {
        providerList.removeAllViews();
        List<ProviderProfile> providers = host.getProviders();
        if (providers.isEmpty()) {
            LinearLayout empty = UiKit.card(getContext());
            TextView title = UiKit.heading(getContext(), "还没有服务商", 18);
            TextView detail = UiKit.text(getContext(),
                    "添加中转站地址、API Key 与模型后即可开始使用。", 14, UiKit.MUTED);
            Button add = UiKit.button(getContext(), "添加第一个服务商", true);
            add.setOnClickListener(view -> host.showProviderEditor(null));
            empty.addView(title);
            empty.addView(detail, UiKit.matchWrap(UiKit.dp(getContext(), 8), UiKit.dp(getContext(), 18)));
            empty.addView(add, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            providerList.addView(empty, UiKit.matchWrap(0, 12));
            return;
        }

        TextView sectionTitle = UiKit.heading(getContext(), "已保存的服务商", 15);
        providerList.addView(sectionTitle, UiKit.matchWrap(2, 10));

        String selectedId = host.getSelectedProvider() == null
                ? "" : host.getSelectedProvider().getId();
        for (ProviderProfile provider : providers) {
            boolean selected = provider.getId().equals(selectedId);
            LinearLayout card = UiKit.card(getContext());
            if (selected) {
                card.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(getContext(), 8),
                        UiKit.ACCENT, UiKit.dp(getContext(), 2)));
            }

            LinearLayout titleRow = UiKit.horizontal(getContext());
            TextView name = UiKit.heading(getContext(), provider.getName(), 17);
            titleRow.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (selected) {
                TextView badge = UiKit.text(getContext(), "使用中", 11, UiKit.ACCENT);
                badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                badge.setPadding(UiKit.dp(getContext(), 8), UiKit.dp(getContext(), 3),
                        UiKit.dp(getContext(), 8), UiKit.dp(getContext(), 3));
                badge.setBackground(UiKit.rounded(UiKit.CANVAS, UiKit.dp(getContext(), 6),
                        UiKit.ACCENT, UiKit.dp(getContext(), 1)));
                titleRow.addView(badge);
            }
            card.addView(titleRow);

            String endpoint = provider.getBaseUrl().isEmpty() ? "未填写 API 地址" : provider.getBaseUrl();
            TextView url = UiKit.text(getContext(), endpoint, 12, UiKit.MUTED);
            url.setMaxLines(1);
            url.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(url, UiKit.matchWrap(UiKit.dp(getContext(), 5), 0));

            String model = provider.getSelectedModel().isEmpty()
                    ? "未选择模型" : provider.getSelectedModel();
            TextView meta = UiKit.text(getContext(),
                    modeLabel(provider.getMode()) + " · " + model, 12, UiKit.MUTED);
            card.addView(meta, UiKit.matchWrap(UiKit.dp(getContext(), 4), UiKit.dp(getContext(), 12)));

            LinearLayout actions = UiKit.horizontal(getContext());
            Button use = UiKit.compactButton(getContext(), selected ? "正在使用" : "使用", !selected);
            use.setEnabled(!selected);
            use.setOnClickListener(view -> {
                host.selectProvider(provider.getId());
                host.showSettings();
            });
            actions.addView(use);

            Button edit = UiKit.compactButton(getContext(), "编辑", false);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            editParams.leftMargin = UiKit.dp(getContext(), 8);
            edit.setOnClickListener(view -> host.showProviderEditor(provider));
            actions.addView(edit, editParams);

            Button delete = UiKit.compactButton(getContext(), "删除", false);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteParams.leftMargin = UiKit.dp(getContext(), 8);
            delete.setTextColor(UiKit.DANGER);
            delete.setOnClickListener(view -> confirmDelete(provider));
            actions.addView(delete, deleteParams);

            card.addView(actions);
            providerList.addView(card, UiKit.matchWrap(0, 10));
        }

        TextView note = UiKit.text(getContext(),
                "API Key 仅保存在本机，并使用 Android Keystore 加密。", 12, UiKit.MUTED);
        note.setGravity(Gravity.CENTER);
        providerList.addView(note, UiKit.matchWrap(UiKit.dp(getContext(), 8), 0));
    }

    private void confirmDelete(ProviderProfile provider) {
        new AlertDialog.Builder(getContext())
                .setTitle("删除服务商")
                .setMessage("确定删除“" + provider.getName() + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    host.deleteProvider(provider.getId());
                    host.showSettings();
                })
                .show();
    }

    private String modeLabel(String mode) {
        return ProviderProfile.MODE_RESPONSES.equals(mode)
                ? "Responses API" : "Chat Completions";
    }
}
