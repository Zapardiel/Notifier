package com.honeywell.tools.notifier;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ExpandableListAdapter;
import android.widget.Toast;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {
    public final static int REQUEST_WRITE_STORAGE = 101;
    public final static int REQUEST_CODE_DRAW = 102;
    public final static int REQUEST_CODE_NOTIFICATIONS = 103;
    private final String TAG = NotificationService.class.getSimpleName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (!Build.MANUFACTURER.equals("Honeywell")) {
                Toast.makeText(this, "Este producto solo corre en equipos Honeywell", Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Error getting the Serial Number", Toast.LENGTH_SHORT).show();
            finish();
        }

        ask4Permissions();
    }

    //
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    // Request Permissions
    // These methods cannot be called in the OnCreate, maybe onResume
    // Call Permissions sequentially, because depending on the type of permission (OppApp or Regular Permission) launches an Async Thread.
    private void ask4Permissions() {
        try {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                setProvisioner("Tasks", "Task", "Run", "", "", "pm grant com.honeywell.tools.notifier android.permission.WRITE_EXTERNAL_STORAGE");
                setProvisioner("Tasks", "Task", "Run", "", "", "pm grant com.honeywell.tools.notifier android.permission.READ_EXTERNAL_STORAGE");
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Exception requestWritePermission: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }

//        // Regular way to ask for WRITE permission
//        if (ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
//        {   ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
//        }

        // Permission for AppOps (No way to use provisioner "pm grant")
        // First has to be asked for NOTIFICATION, and then asked for DRAW OVERLAY
        try {
            checkNotificationListenerPermission();
        } catch (Exception ex) {
            NotificationService.appendLog("ask4Permissions Notification access Exception: " + ex.getMessage());
            finish();
        }

    }

    // Callback after request WRITE_EXTERNAL_STORAGE permission
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Log.e("BatLog", "Permissions Granted");
                    } else {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            Toast.makeText(MainActivity.this, "Sorry! Go to Apps->Permissions", Toast.LENGTH_SHORT).show();
                            return;
                        } else {
                            // It was checked "Never Ask Again!"
                            Toast.makeText(MainActivity.this, "Sorry! Go to Apps->Permissions", Toast.LENGTH_SHORT).show();
                        }
                        finish();
                    }
                }
                break;
        }
    }

    // Ask for: Notification Access Permission
    public void checkNotificationListenerPermission() {
        try {
            if (!canNotificationListener()) {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivityForResult(intent, REQUEST_CODE_NOTIFICATIONS);
            }else{
                if (!Settings.canDrawOverlays(this)){
                    checkDrawOverlayPermission();
                }else{
                    finish();
                }
            }
        } catch (Exception ex) {
            NotificationService.appendLog("checkNotificationListenerPermission Exception: " + ex.getMessage());
        }
    }

    // Ask for: Draw Overlay Permission
    public void checkDrawOverlayPermission() {
        try {
            // Checks if app already has permission to draw overlays
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_DRAW);
            }
        } catch (Exception ex) {
            NotificationService.appendLog("checkDrawOverlayPermission Exception " + ex.getMessage());
        }
    }

    //
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        try {
            // Check if the NOTIFICATION ACCESS permission is granted
            if (requestCode == REQUEST_CODE_NOTIFICATIONS) {
                try {
                    if (!canNotificationListener()) {
                        NotificationService.appendLog("PERMISSION NOT GRANTED: Notification Access");
                    }
                    checkDrawOverlayPermission();
                } catch (Exception ex) {
                    NotificationService.appendLog("onActivityResult Notification Access Exception: " + ex.getMessage());
                    finish();
                }
            } else if (requestCode == REQUEST_CODE_DRAW) {
                // Check if the DRAW OVERLAY permission is grranted
                try {
                    if (!Settings.canDrawOverlays(this)) {
                        NotificationService.appendLog("PERMISSION NOT GRANTED: Draw Overlay");
                    }

                    // Remove Provisioner Notification
                    try {
                        setProvisioner("CommandTask", "", "ClearAllTasks", "", "", "");
                    } catch (Exception ex) {
                        NotificationService.appendLog("Exception requestWritePermission: " + ex.getMessage());
                    }

                } catch (Exception ex) {
                    NotificationService.appendLog("onActivityResult Draw Overlay Exception:" + ex.getMessage());
                }finally {
                    finish();
                }
            }
        } catch (Exception ex) {
            NotificationService.appendLog("Exception onActivityResult: " + ex.getMessage());
        }
    }

    // Checks that the Notification Access was granted previously
    private boolean canNotificationListener() {
        String theList = null;
        String[] theListList;
        try {
            theList = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        } catch (Exception ex) {
            NotificationService.appendLog("canNotificationListener Exception Listeners: " + ex.getMessage());
        }
        if(theList==null) return false; //Nothing in the list
        theListList = theList.split(":");
        String me = (new ComponentName(this, NotificationService.class)).flattenToString();
        try {
            for (String next : theListList) {
                if (me.equals(next)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            NotificationService.appendLog("canNotificationListener Exception FOR: " + ex.getMessage());
        }
        return false;
    }

    // region UTILS
    // We use Provisioner service to grant permissions on EDA50, but we CAN'T on EDA51
    private void setProvisioner(String tskType, String tskSubType, String tskAction, String tskSource, String tskDestination, String
            tskLaunch) {
        try {
            Intent intent;
            intent = new Intent("com.honeywell.tools.provisioner.intent.action.IMPORT");

            String sXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
            sXml += "<ConfigDoc name=\"Provisioner\">";
            if (!tskType.isEmpty()) sXml += "<Section name=\"" + tskType + "\">";
            if (!tskSubType.isEmpty()) sXml += "<Section name=\"" + tskSubType + "\">";
            if (!tskSource.isEmpty()) sXml += "<Key name=\"Source\">" + tskSource + "</Key>";
            if (!tskDestination.isEmpty())
                sXml += "<Key name=\"Destination\">" + tskDestination + "</Key>";
            if (!tskAction.isEmpty()) sXml += "<Key name=\"Action\">" + tskAction + "</Key>";
            if (!tskLaunch.isEmpty()) sXml += "<Key name=\"Launch\">" + tskLaunch + "</Key>";
            if (!tskSubType.isEmpty()) sXml += "</Section>";
            if (!tskType.isEmpty()) sXml += "</Section>";
            sXml += "</ConfigDoc>";
            intent.putExtra("com.honeywell.tools.provisioner.EXTRA_XML", sXml);
            this.getBaseContext().sendBroadcast(intent);
            Log.d(TAG, "Sent Broadcast");
        } catch (Exception ee) {
            Log.d(TAG, "TestException - " + ee.toString());
        }
    }
    // endregion
}
