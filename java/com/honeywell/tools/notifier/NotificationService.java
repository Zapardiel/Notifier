package com.honeywell.tools.notifier;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class NotificationService extends NotificationListenerService {
    private final String TAG = NotificationService.class.getSimpleName();
    Bundle extras;
    public static String[] str2Show;
    public static String[] str2DontKill;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            iniFile ini = new iniFile("/sdcard/Notifier.ini");
            str2Show = ini.getString("SETTINGS", "SHOW", "xone").split(";");
            str2DontKill = ini.getString("SETTINGS", "DONT_KILL", "teclado").split(";");
        } catch (IOException e) {
            Log.d(TAG, "onCreate: File IOException " + e.getMessage());
        }
    }

    private class myView {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        View floatyView;

        public void addOverlayView(String[] extraInfo, final Icon myIcon, final PendingIntent myPi) {

            // View Params (Transparency, Type, etc)
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.RGB_565);
            params.gravity = Gravity.CENTER | Gravity.START;
            params.alpha = 1; // Opaque. Don't use PixelFormat=OPAQUE
            params.x = 0;
            params.y = 0;

            // Inner Class - New LinearLayout view
            LinearLayout interceptorLayout = new LinearLayout(NotificationService.this) {

                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    // Only fire on the ACTION_DOWN event, or you'll get two events (one for _DOWN, one for _UP)
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {

                        // Check if the BACK button is pressed
                        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                            Log.d(TAG, "BACK Button Pressed");
                            if (floatyView != null) {
                                windowManager.removeView(floatyView);
                                floatyView = null;
                            }
                            // As we've taken action, we'll return true to prevent other apps from consuming the event as well
                            return true;
                        }
                    }
                    // Otherwise don't intercept the event
                    return super.dispatchKeyEvent(event);
                }
            };

            // Inflate Layout in the View
            floatyView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                    .inflate(R.layout.floating_view, interceptorLayout, true);

            // Listener to Removes the View when it's touched.
            try {
                floatyView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            if (myPi != null) {
                                myPi.send();
                            }
                        }catch (Exception ex){
                            appendLog("Launching Pending Intent Exception");
                        }
                        windowManager.removeView(floatyView);
                        floatyView = null;
                    }
                });
            } catch (Exception ex) {
                appendLog("addOverlayView Exception onPackageName: " + ex.getMessage());
            }

            // Set Title
            if (extraInfo[0] != null) {
                TextView txtInfo = (TextView) interceptorLayout.findViewById(R.id.notTitle);
                txtInfo.setText(extraInfo[0]);
            }

            // Set Description
            if (extraInfo[1] != null) {
                TextView txtInfo = (TextView) interceptorLayout.findViewById(R.id.notText);
                txtInfo.setText(extraInfo[1]);
            }

            if (myIcon != null) {
                ImageView imgView = (ImageView) interceptorLayout.findViewById(R.id.imgView);
                imgView.setImageIcon(myIcon);
            }

            // Shows only non-registered packageNames
            try {
                Log.d(TAG, "PackageName: " + extraInfo[2]);
                if (floatyView != null) windowManager.addView(floatyView, params);
            } catch (Exception ex) {
                appendLog("addOverlayView Exception: " + ex.getMessage());
            }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        Notification mNotification = sbn.getNotification();
        if (mNotification != null) {
            String[] list = new String[3];
            extras = mNotification.extras;
            Icon myIcon = null;

            try {
                if (mNotification.getLargeIcon() != null) {
                    myIcon = (Icon) mNotification.getLargeIcon();
                } else {
                    myIcon = (Icon) mNotification.getSmallIcon();
                }
            } catch (Exception ex) {
                appendLog("onNotificationPosted Exception Image: " + ex.getMessage());
            }
            try {
                if (extras.getCharSequence("android.title") != null)
                    list[0] = extras.getCharSequence("android.title").toString();
            } catch (Exception ex) {
                appendLog("onNotificationPosted Exception onTitle: " + ex.getMessage());
            }
            try {
                if (extras.getCharSequence("android.text") != null)
                    list[1] = extras.getCharSequence("android.text").toString();
            } catch (Exception ex) {
                appendLog("onNotificationPosted Exception onText: " + ex.getMessage());
            }
            try {
                if (sbn.getPackageName() != null)
                    list[2] = sbn.getPackageName();
            } catch (Exception ex) {
                appendLog("onNotificationPosted Exception onPackageName: " + ex.getMessage());
            }


            // Temporary Log PackageName in sdcard
            appendLog("onNotificationPosted Title: " + list[0]);
            appendLog("onNotificationPosted Text: " + list[1]);

            //Check if we can Draw Overlays
            if (!Settings.canDrawOverlays(this)) {
                appendLog("onNotificationPosted No se ha activado Draw Overlays, pero se pueden leer notificaciones");

            } else {
                // Show the View
                try {
                    if (list[0] != null) {
                        boolean match = false;
                        if (str2Show != null) {
                            for (String s : str2Show) {
                                if (list[0].toLowerCase().contains(s)) {
                                    Log.d(TAG, "onNotificationPosted Match: " + list[0].toString());
                                    match = true;
                                }
                            }
                        } else {
                            appendLog("onNotificationPosted str2DontShow is Empty");
                        }
                        if (match) {
                            PendingIntent pi=null;
                            try {
                                Notification n = sbn.getNotification();
                                if (n.contentIntent != null) {
                                    pi = n.contentIntent;
                                }
                            } catch (Exception ex) {
                                appendLog("Gathering PendingIntent Exception: " + ex.getMessage());
                            }

                            myView myview = new myView();
                            myview.addOverlayView(list, myIcon, pi);
                        }
                    }
                } catch (Exception ex) {
                    appendLog("onNotificationPosted Exception addOverlayView: " + ex.getMessage());
                }
            }

            // Clears orphan views from memory
            System.gc();

            // Clear all notifications.
            try {
                if (list[0] != null) {
                    boolean match = false;
                    if (str2DontKill != null) {
                        for (String s : str2DontKill) {
                            if (list[0].toLowerCase().contains(s)) {
                                Log.d(TAG, "onNotificationPosted Match: " + list[0].toString());
                                match = true;
                            }
                        }
                    } else {
                        appendLog("onNotificationPosted str2DontKill is Empty");
                    }
                    if (!match)
                        cancelAllNotifications();
                }
            } catch (Exception ex) {
                appendLog("onNotificationPosted Exception ClosingNots: " + ex.getMessage());
            }
        }
    }


//        Intent intent = getPackageManager().getLaunchIntentForPackage(
//                sbn.getPackageName());
//        if (intent != null) {
//            Log.i(TAG, "Launching intent " + intent + " package name: "
//                    + sbn.getPackageName());
//        }


    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    //region Logging
    static void appendLog(String info) {
        Date curDate = new Date();
        SimpleDateFormat format_date = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat format_hour = new SimpleDateFormat("hh:mm:ss");
        String DateToStr = format_hour.format(curDate);
        File logFile = new File("sdcard", "Notifier_" + format_date.format(curDate) + ".txt");

        if (!logFile.exists()) {
            // delete old files Notifier_xxx.txt
            deleteOldFiles(5);

            // create a new file Notifier__yyyyMMdd.txt
            try {
                logFile.createNewFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(DateToStr + ": " + info);
            buf.newLine();
            buf.flush();
            buf.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static class Pair implements Comparable {
        private long t;
        private File f;

        public Pair(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(Object o) {
            long u = ((NotificationService.Pair) o).t;
            return t < u ? -1 : t == u ? 0 : 1;
        }
    }

    ;

    static void deleteOldFiles(int maxFiles) {
        File dir = new File("sdcard");
        File[] files = dir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.matches("Notifier_(.*).txt");
            }
        });

        if (files == null) return;

        NotificationService.Pair[] pairs = new NotificationService.Pair[files.length];
        for (int i = 0; i < files.length; i++)
            pairs[i] = new NotificationService.Pair(files[i]);

        // Sort them by timestamp.
        Arrays.sort(pairs);

        // Take the sorted pairs and extract only the file part, discarding the timestamp.
        if (files.length > maxFiles) {
            for (int i = 0; i < files.length - maxFiles; i++) {
                pairs[i].f.delete();
            }
        }
    }
    //endregion
}
