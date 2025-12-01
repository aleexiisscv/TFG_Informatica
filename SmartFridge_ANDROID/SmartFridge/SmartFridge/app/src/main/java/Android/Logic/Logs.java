package Android.Logic;

import android.util.Log;

public class Logs {

    private static final String TAG = "SmartFridge";

    public static void info(String TAG, String message) {
        Log.i(TAG, message);
    }

    public static void debug(String TAG, String message) {
        Log.d(TAG, message);
    }

    public static void warn(String TAG, String message) {
        Log.w(TAG, message);
    }

    public static void error(String message, String s) {
        Log.e(TAG, message+" "+s);
    }

    public static void error(String TAG, String message,String m, String s) {
        Log.e(TAG, message+" m: "+m+" s: "+s);
    }
}