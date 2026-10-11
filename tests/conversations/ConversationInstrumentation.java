package com.relaychat.app.conversations;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.relaychat.app.MainActivity;
import com.relaychat.app.data.ChatSession;
import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.Conversation;
import com.relaychat.app.ui.ConversationListScreen;
import com.relaychat.app.ui.UiKit;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/** Run on a disposable emulator; restores the original conversation collection afterward. */
public final class ConversationInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Conversation first;
    private Conversation second;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        List<Conversation> original = new ArrayList<>();
        String[] originalId = new String[1];
        try {
            Intent launch = getTargetContext().getPackageManager()
                    .getLaunchIntentForPackage(getTargetContext().getPackageName());
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(launch);
            runOnMainSync(() -> {
                ((android.view.inputmethod.InputMethodManager) activity.getSystemService(
                        android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
                                activity.getWindow().getDecorView().getWindowToken(), 0);
                original.addAll(activity.getConversations());
                originalId[0] = activity.getActiveConversationId();
                activity.getConversations().clear();
                first = new Conversation();
                first.setTitle("\u8be6\u7ec6\u89e3\u7b54");
                first.getMessages().add(new ChatMessage(ChatMessage.ROLE_USER, "Test message"));
                second = new Conversation();
                second.setTitle("\u624b\u673a\u63a7\u5c4f\u6709\u4ec0\u4e48\u8f6f\u4ef6\u63a8\u8350\u5417\uff1f"
                        + "\u8fd9\u662f\u4e00\u4e2a\u7528\u6765\u6d4b\u8bd5\u957f\u6807\u9898\u7684\u5bf9\u8bdd");
                second.getMessages().add(new ChatMessage(ChatMessage.ROLE_USER, "Another message"));
                activity.getConversations().add(first);
                activity.getConversations().add(second);
                activity.getChatSession().setActiveId(first.getId());
                activity.showConversations();
            });
            waitForIdleSync();
            verifyCompactRows();
            save("list.png");
            verifyMenus();
            verifyRenameAndDelete();
            verifyBottomRow();
            verifyQuickCreate();
            result.putString("stream",
                    "PASS: compact rows, tap, long-press, actions, dismissal, rename, delete, bottom placement,"
                            + " quick create, empty reuse, streaming guard, header layout, compose icon\n");
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
        } finally {
            if (activity != null && originalId[0] != null) {
                runOnMainSync(() -> {
                    activity.getConversations().clear();
                    activity.getConversations().addAll(original);
                    activity.getChatSession().setActiveId(originalId[0]);
                    activity.persistConversations();
                    activity.showChat();
                });
            }
        }
        finish(result.getString("stream", "").startsWith("PASS:")
                ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void verifyCompactRows() {
        runOnMainSync(() -> {
            ConversationListScreen screen = screen();
            require(findText(screen, "\u6253\u5f00") == null, "Open button visible in list");
            require(findText(screen, "\u91cd\u547d\u540d") == null, "Rename button visible in list");
            require(findText(screen, "\u5220\u9664") == null, "Delete button visible in list");
            require(findText(screen, "\u5f53\u524d") != null, "Active badge missing");
            View row = row(second);
            require(row.getHeight() < UiKit.dp(activity, 130), "Conversation row is not compact");
            TextView title = findText(row, second.getTitle());
            require(title.getLineCount() == 1, "Long title wraps");
            require(title.getEllipsize() != null, "Long title is not ellipsized");
        });
    }

    private void verifyMenus() throws Exception {
        press(row(second), true);
        Dialog dialog = menu();
        require(dialog != null && dialog.isShowing(), "Real long-press did not show menu");
        runOnMainSync(() -> {
            View decor = dialog.getWindow().getDecorView();
            require(findText(decor, "\u6253\u5f00") != null, "Open action missing");
            require(findText(decor, "\u91cd\u547d\u540d") != null, "Rename action missing");
            require(findText(decor, "\u5220\u9664") != null, "Delete action missing");
            require(findText(decor, second.getTitle()) != null, "Preview missing");
        });
        verifyBounds(dialog);
        save("menu.png");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        waitForIdleSync();
        require(menu() == null && screen() != null, "Back did not just dismiss menu");

        press(row(second), true);
        tapOutside();
        require(menu() == null, "Outside tap did not dismiss menu");
        press(row(first), true);
        Dialog activeMenu = menu();
        runOnMainSync(() -> {
            TextView label = findText(activeMenu.getWindow().getDecorView(),
                    "\u5f53\u524d\u5bf9\u8bdd");
            require(label != null && !((View) label.getParent()).isEnabled(),
                    "Active conversation action should be disabled");
        });
        save("active-menu.png");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        press(row(second), false);
        require(activity.getActiveConversationId().equals(second.getId()), "Tap did not open row");
        runOnMainSync(activity::showConversations);
        waitForIdleSync();
        press(row(first), true);
        click(menu(), "\u6253\u5f00");
        require(activity.getActiveConversationId().equals(first.getId()), "Menu open failed");
        runOnMainSync(activity::showConversations);
        waitForIdleSync();
    }

    private void verifyRenameAndDelete() throws Exception {
        press(row(second), true);
        click(menu(), "\u91cd\u547d\u540d");
        View rename = focusedDecor();
        runOnMainSync(() -> {
            EditText input = findEdit(rename);
            require(input != null, "Rename input missing");
            input.setText("Renamed conversation");
            findText(rename, "\u4fdd\u5b58").performClick();
        });
        waitForIdleSync();
        require(second.getTitle().equals("Renamed conversation"), "Rename not applied");
        press(row(second), true);
        click(menu(), "\u5220\u9664");
        View delete = focusedDecor();
        runOnMainSync(() -> findText(delete, "\u53d6\u6d88").performClick());
        waitForIdleSync();
        require(activity.getConversations().contains(second), "Cancel deleted conversation");
        press(row(second), true);
        click(menu(), "\u5220\u9664");
        View confirmed = focusedDecor();
        runOnMainSync(() -> findText(confirmed, "\u5220\u9664").performClick());
        waitForIdleSync();
        require(!activity.getConversations().contains(second), "Confirmed delete failed");
        require(activity.getActiveConversationId().equals(first.getId()), "Delete changed active chat");
    }

    private void verifyBottomRow() throws Exception {
        Conversation last = new Conversation();
        last.setTitle("Bottom conversation");
        runOnMainSync(() -> {
            for (int i = 0; i < 18; i++) {
                Conversation extra = new Conversation();
                extra.setTitle("Conversation " + i);
                extra.getMessages().add(new ChatMessage(ChatMessage.ROLE_USER, "Extra message " + i));
                activity.getConversations().add(extra);
            }
            last.getMessages().add(new ChatMessage(ChatMessage.ROLE_USER, "Bottom message"));
            activity.getConversations().add(last);
            activity.showConversations();
        });
        waitForIdleSync();
        String activeId = activity.getActiveConversationId();
        View swipeRow = row(first);
        int[] swipePoint = new int[2];
        runOnMainSync(() -> {
            swipeRow.getLocationOnScreen(swipePoint);
            swipePoint[0] += swipeRow.getWidth() / 2;
            swipePoint[1] += swipeRow.getHeight() / 2;
        });
        swipe(swipePoint[0], swipePoint[1]);
        require(menu() == null, "Scrolling opened the long-press menu");
        require(activity.getActiveConversationId().equals(activeId), "Scrolling opened a conversation");
        runOnMainSync(() -> ((ScrollView) screen().getChildAt(1)).fullScroll(View.FOCUS_DOWN));
        waitForIdleSync();
        SystemClock.sleep(400);
        press(row(last), true);
        require(menu() != null, "Bottom row menu missing");
        verifyBounds(menu());
        save("bottom-menu.png");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        Dialog[] detached = new Dialog[1];
        runOnMainSync(() -> {
            row(last).performLongClick();
            try {
                detached[0] = menu();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
            activity.showChat();
        });
        require(screen() == null, "Navigation failed");
        require(!detached[0].isShowing(), "Menu survived screen detachment");
    }

    private ConversationListScreen screen() {
        return findScreen(activity.getWindow().getDecorView());
    }

    private void verifyQuickCreate() throws Exception {
        runOnMainSync(() -> {
            activity.getChatSession().setActiveId(first.getId());
            activity.showChat();
        });
        waitForIdleSync();
        SystemClock.sleep(350);
        View root = activity.getWindow().getDecorView();
        Button create = root.findViewWithTag("new_conversation_button");
        require(create != null && create.getText().length() == 0, "New-chat icon button missing");
        runOnMainSync(() -> {
            LinearLayout header = (LinearLayout) create.getParent();
            View conversations = root.findViewWithTag("conversation_button");
            View settings = root.findViewWithTag("settings_button");
            TextView title = findText(header, "RelayChat");
            require(title != null && title.getLayout().getEllipsisCount(0) == 0,
                    "Brand is clipped by header actions");
            require(header.indexOfChild(create) == 1 && create.getWidth() == UiKit.dp(activity, 44),
                    "New-chat icon has the wrong position or width");
            require(create.getRight() <= conversations.getLeft()
                    && conversations.getRight() <= settings.getLeft()
                    && settings.getRight() <= header.getWidth() - header.getPaddingRight(),
                    "Header actions overlap or overflow");
            Bitmap icon = Bitmap.createBitmap(create.getWidth(), create.getHeight(),
                    Bitmap.Config.ARGB_8888);
            create.draw(new Canvas(icon));
            int pixels = 0;
            for (int y = 0; y < icon.getHeight(); y++) {
                for (int x = 0; x < icon.getWidth(); x++) {
                    int color = icon.getPixel(x, y);
                    if (Color.alpha(color) > 128 && Color.red(color) < 100) pixels++;
                }
            }
            require(pixels > 30, "Compose icon is blank");
            icon.recycle();
        });
        save("chat-header.png");
        int count = activity.getConversations().size();
        press(create, false);
        require(activity.getConversations().size() == count + 1, "Quick create did not add conversation");
        String createdId = activity.getActiveConversationId();
        require(!createdId.equals(first.getId()) && activity.getActiveConversation().getMessages().isEmpty(),
                "Quick create did not switch to an empty conversation");
        require(activity.getConversations().contains(first) && first.getMessages().size() == 1,
                "Quick create changed the previous conversation");
        runOnMainSync(activity::showConversations);
        waitForIdleSync();
        runOnMainSync(() -> require(findText(screen(), "新对话") == null,
                "Empty conversation is visible in the conversation list"));
        runOnMainSync(activity::showChat);
        waitForIdleSync();
        press(create, false);
        require(activity.getConversations().size() == count + 1
                && activity.getActiveConversationId().equals(createdId), "Empty conversation duplicated");

        Field request = ChatSession.class.getDeclaredField("request");
        request.setAccessible(true);
        Constructor<?> constructor = Class.forName("com.relaychat.app.data.ChatSession$Request")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object pending = constructor.newInstance(1L, createdId, null, new ArrayList<ChatMessage>(),
                new ChatMessage(ChatMessage.ROLE_ASSISTANT, ""));
        try {
            runOnMainSync(() -> {
                activity.getHistory().add(new ChatMessage(ChatMessage.ROLE_USER, "Pending conversation"));
                try {
                    request.set(activity.getChatSession(), pending);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
                create.performClick();
            });
            require(activity.getActiveConversationId().equals(createdId)
                    && activity.getConversations().size() == count + 1, "New-chat bypassed streaming guard");
        } finally {
            runOnMainSync(() -> {
                try {
                    request.set(activity.getChatSession(), null);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }

    private ConversationListScreen findScreen(View view) {
        if (view instanceof ConversationListScreen) return (ConversationListScreen) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ConversationListScreen found = findScreen(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private View row(Conversation conversation) {
        TextView name = findText(screen(), conversation.getTitle());
        require(name != null, "Conversation title missing");
        return (View) name.getParent().getParent();
    }

    private Dialog menu() throws Exception {
        ConversationListScreen screen = screen();
        if (screen == null) return null;
        Field field = ConversationListScreen.class.getDeclaredField("actionsDialog");
        field.setAccessible(true);
        return (Dialog) field.get(screen);
    }

    private View focusedDecor() throws Exception {
        Field managerField = Class.forName("android.view.WindowManagerGlobal")
                .getDeclaredField("sDefaultWindowManager");
        managerField.setAccessible(true);
        Object manager = managerField.get(null);
        Field viewsField = manager.getClass().getDeclaredField("mViews");
        viewsField.setAccessible(true);
        List<?> views = (List<?>) viewsField.get(manager);
        for (int i = views.size() - 1; i >= 0; i--) {
            View view = (View) views.get(i);
            if (view.hasWindowFocus()) return view;
        }
        throw new AssertionError("Focused dialog missing");
    }

    private void press(View view, boolean longPress) {
        int[] point = new int[2];
        runOnMainSync(() -> {
            view.getLocationOnScreen(point);
            point[0] += view.getWidth() / 2;
            point[1] += view.getHeight() / 2;
        });
        gesture(point[0], point[1], longPress ? ViewConfiguration.getLongPressTimeout() + 200 : 70);
    }

    private void tapOutside() {
        View decor = screen().getChildAt(0);
        int[] point = new int[2];
        runOnMainSync(() -> {
            decor.getLocationOnScreen(point);
            point[0] += decor.getWidth() / 2;
            point[1] += decor.getHeight() / 2;
        });
        gesture(point[0], point[1], 70);
    }

    private void gesture(int x, int y, int duration) {
        long down = SystemClock.uptimeMillis();
        MotionEvent start = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0);
        require(getUiAutomation().injectInputEvent(start, true), "Pointer down injection failed");
        start.recycle();
        SystemClock.sleep(duration);
        MotionEvent end = MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, x, y, 0);
        require(getUiAutomation().injectInputEvent(end, true), "Pointer up injection failed");
        end.recycle();
        waitForIdleSync();
        SystemClock.sleep(250);
    }

    private void swipe(int x, int y) {
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= 8; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN
                    : i == 8 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    x, y - UiKit.dp(activity, i * 8), 0);
            require(getUiAutomation().injectInputEvent(event, true), "Swipe injection failed");
            event.recycle();
            SystemClock.sleep(20);
        }
        waitForIdleSync();
        SystemClock.sleep(500);
    }

    private void click(Dialog dialog, String label) {
        runOnMainSync(() -> {
            TextView text = findText(dialog.getWindow().getDecorView(), label);
            require(text != null, "Missing action " + label);
            ((View) text.getParent()).performClick();
        });
        waitForIdleSync();
        SystemClock.sleep(250);
    }

    private void verifyBounds(Dialog dialog) {
        runOnMainSync(() -> {
            Rect frame = new Rect();
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
            View decor = dialog.getWindow().getDecorView();
            int[] point = new int[2];
            decor.getLocationOnScreen(point);
            require(point[0] >= frame.left && point[1] >= frame.top
                    && point[0] + decor.getWidth() <= frame.right
                    && point[1] + decor.getHeight() <= frame.bottom, "Menu outside visible window");
        });
    }

    private TextView findText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(text)) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private EditText findEdit(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEdit(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void save(String name) throws Exception {
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        File directory = new File(getTargetContext().getExternalFilesDir(null), "conversation-tests");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("No output");
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
