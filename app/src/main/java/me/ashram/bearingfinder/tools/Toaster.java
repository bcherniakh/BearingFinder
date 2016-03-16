package me.ashram.bearingfinder.tools;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by Cherniakh Bohdan on 21.12.2015.
 */
public class Toaster {
    private Context context;

    public Toaster(Context context) {
        this.context = context;
    }

    public void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public void showLongToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
