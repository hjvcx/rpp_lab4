package com.example.wdget;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class CountDays extends AppWidgetProvider {
    public static final String ACTION_SCHEDULED_ALARM ="com.example.wdget.SCHEDULED_ALARM";
    private static final String ACTION_SCHEDULED_UPDATE ="com.example.wdget.SCHEDULED_UPDATE";
    private static final String TAG = "CountDays.java";
    private static final int DAY_OF_MONTH = (24 * 60 * 60 * 1000);
    private static boolean restartFlag = false;

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, CountDays.class);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction()!= null) {
            switch (intent.getAction()) {
                case Intent.ACTION_BOOT_COMPLETED:
                    restartFlag = true;
                case Intent.ACTION_TIMEZONE_CHANGED:
                case Intent.ACTION_TIME_CHANGED:

                case ACTION_SCHEDULED_UPDATE:
                    AppWidgetManager manager = AppWidgetManager.getInstance(context);
                    int[] ids = manager.getAppWidgetIds(getComponentName(context));
                    Log.i(TAG,"Size:" + ids.length);
                    onUpdate(context, manager, ids);
                    break;

                case ACTION_SCHEDULED_ALARM:
                    AppWidgetManager manager1 = AppWidgetManager.getInstance(context);
                    int id = (manager1.getAppWidgetIds(getComponentName(context)))[0];
                    MainActivity.setNotifShown(context,id,true);

                    showNotification(context);

                    break;
            }
        }

        super.onReceive(context,intent);
    }


    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            MainActivity.deleteDatePref(context, appWidgetId);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.day_count);
        views.setOnClickPendingIntent(R.id.contentFrame,pendingIntent);



        if (!MainActivity.isDone(context,appWidgetId)) {

            Calendar calendar = Calendar.getInstance();
            String widgetDate = MainActivity.loadDatePref(context, appWidgetId);

            long timeInMilliseconds = 0l;
            if (!widgetDate.equals("")) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                try {
                    Date mDate = sdf.parse(widgetDate);
                    timeInMilliseconds = mDate.getTime();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }


            double diffInDays = (double) (timeInMilliseconds - calendar.getTimeInMillis()) / DAY_OF_MONTH;

            int daysLeftCeil = (int) Math.ceil(diffInDays);
            views.setTextViewText(R.id.counterTv, String.valueOf(Math.max(0, daysLeftCeil)));


            Log.i(TAG, "Days diff for W:(" + appWidgetId + "): " + String.format(Locale.getDefault(), "%.2f", diffInDays));
            if (daysLeftCeil == 0) {
                scheduleAlarm(context, appWidgetId);
                MainActivity.setDone(context, appWidgetId, true);

            } else if (diffInDays > 0) {

                if (diffInDays < 100) {
                    views.setTextViewTextSize(R.id.counterTv, TypedValue.COMPLEX_UNIT_SP, 95);
                    views.setViewPadding(R.id.counterTv, 0, 0, 0, 0);

                } else {
                    int bottomMargin = (int) context.getResources().getDimension(R.dimen.counter_text_view_bottom_margin);
                    views.setTextViewTextSize(R.id.counterTv, TypedValue.COMPLEX_UNIT_SP, 65);
                    views.setViewPadding(R.id.counterTv, 0, 0, 0, bottomMargin);
                }


                scheduleNextUpdate(context, appWidgetId);
            }

        } else {
            views.setTextViewText(R.id.counterTv, String.valueOf(0));
            boolean alarmShown = MainActivity.wasNotifShown(context, appWidgetId);
            if (restartFlag && !alarmShown) {
                scheduleAlarm(context, appWidgetId);
                restartFlag = false;
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }


    private static void scheduleNextUpdate(Context context, int appWidgetId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, CountDays.class).setAction(ACTION_SCHEDULED_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        long midnightTime = getTimeTillHour(0) + DAY_OF_MONTH;
        Log.i(TAG, "Update for W:(" + appWidgetId + ") at, in ms : " + midnightTime);

        alarmManager.cancel(pendingIntent);

        alarmManager.set(AlarmManager.RTC, midnightTime, pendingIntent);
    }

    private static void scheduleAlarm(Context context, int appWidgetId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, CountDays.class).setAction(ACTION_SCHEDULED_ALARM);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        alarmManager.cancel(pendingIntent);

        long alarmTime = getTimeTillHour(9);
        Log.i(TAG, "Alarm for W:(" + appWidgetId + ") at, in ms : " + alarmTime);

        alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
    }


    private static long getTimeTillHour(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);

        calendar.set(Calendar.SECOND, 1);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTimeInMillis();
    }

    private static void showNotification(Context context) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String CHANNEL_ID = "alarm_ch_1";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mNotificationManager.createNotificationChannel( createNotificationChannel(CHANNEL_ID) );
        }

        NotificationCompat.Builder mBuilder =   new NotificationCompat.Builder(context,CHANNEL_ID)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_event)
                .setContentTitle("Событие сегодня!")
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0))
                .setAutoCancel(true);

        mNotificationManager.notify(1, mBuilder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static NotificationChannel createNotificationChannel(String CHANNEL_ID) {
        NotificationChannel mChannel = new NotificationChannel(
                CHANNEL_ID,
                "Widget Alarm",
                NotificationManager.IMPORTANCE_DEFAULT);
        mChannel.setDescription("Widget Alarm");
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.YELLOW);
        mChannel.enableVibration(true);
        mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
        mChannel.setShowBadge(false);

        return mChannel;
    }
}