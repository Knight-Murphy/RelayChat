package com.relaychat.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.Manifest;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.relaychat.app.data.ChatSession;

/** Keeps an explicitly requested reply running after its Activity has gone away. */
public final class ReplyService extends Service {
    public static final String EXTRA_REQUEST_ID = "request_id";
    private static final String ACTION_STOP = "com.relaychat.app.STOP_REPLY";
    private static final String CHANNEL_ID = "reply_generation";
    private static final String COMPLETION_CHANNEL_ID = "reply_completed";
    private static final int NOTIFICATION_ID = 1;
    private static final int COMPLETION_NOTIFICATION_ID = 2;
    private ChatSession session;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private boolean foreground;
    private long requestId;
    private final ChatSession.Listener listener = () -> {
        if (!session.isSending()) {
            if (session.didReplySucceed(requestId)) {
                showCompletionNotification();
            }
            stopWork();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        session = ((ChatApplication) getApplication()).getSession();
        session.addListener(listener);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "后台回答", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("回答生成时保持连接，完成后自动结束");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            NotificationChannel completed = new NotificationChannel(COMPLETION_CHANNEL_ID,
                    "回答完成提醒", NotificationManager.IMPORTANCE_DEFAULT);
            completed.setDescription("后台回答完成后提示查看");
            getSystemService(NotificationManager.class).createNotificationChannel(completed);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long id = intent == null ? 0 : intent.getLongExtra(EXTRA_REQUEST_ID, 0);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            if (id == session.getRequestId()) {
                session.cancelReply();
            }
            if (!session.isSending()) {
                stopWork();
            }
            return START_NOT_STICKY;
        }
        // Promotion also fulfills the foreground-service contract for a cancelled queued start.
        try {
            requestId = session.getRequestId();
            Notification notification = buildNotification(session.getRequestId());
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foreground = true;
            if (!session.isSending()) {
                stopWork();
            } else {
                if (wakeLock == null) {
                    PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "RelayChat:reply");
                    wakeLock.acquire(6 * 60 * 60 * 1000L);
                }
                acquireWifiLock();
                session.beginReply(id);
            }
        } catch (RuntimeException error) {
            session.abortReply("后台回答服务启动失败：" + error.getMessage());
            stopWork();
        }
        // Do not replay a billed request if Android terminates the process.
        return START_NOT_STICKY;
    }

    private Notification buildNotification(long id) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAction = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ReplyService.class).setAction(ACTION_STOP)
                .putExtra(EXTRA_REQUEST_ID, id);
        PendingIntent stopAction = PendingIntent.getService(this, (int) id, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_reply_notification)
                .setContentTitle("RelayChat 正在生成回答")
                .setContentText("可退出页面，回答将在后台继续；点击返回对话")
                .setContentIntent(openAction)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "停止", stopAction).build())
                .build();
    }

    private void acquireWifiLock() {
        if (wifiLock != null) {
            return;
        }
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null) {
                WifiManager.WifiLock lock = wifi.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RelayChat:reply-wifi");
                lock.setReferenceCounted(false);
                lock.acquire();
                wifiLock = lock;
            }
        } catch (RuntimeException error) {
            // Wi-Fi support varies by device. Cellular replies must still be allowed to run.
            Log.w("RelayChat", "Could not acquire reply Wi-Fi lock", error);
        }
    }

    private void releaseLocks() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        wifiLock = null;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void showCompletionNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent open = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_CONVERSATION_ID, session.getCompletedConversationId())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent action = PendingIntent.getActivity(this, COMPLETION_NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, COMPLETION_CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(R.drawable.ic_reply_notification)
                .setContentTitle("RelayChat 回答已完成")
                .setContentText("回答已保存，点击查看")
                .setContentIntent(action)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();
        getSystemService(NotificationManager.class).notify(
                COMPLETION_NOTIFICATION_ID, notification);
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        session.abortReply("后台运行时间已达到系统限制，已保留收到的回答");
        stopWork();
    }

    private void stopWork() {
        releaseLocks();
        if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        session.removeListener(listener);
        if (session.getRequestId() == requestId) {
            session.abortReply("后台服务已结束，已保留收到的回答");
        }
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
