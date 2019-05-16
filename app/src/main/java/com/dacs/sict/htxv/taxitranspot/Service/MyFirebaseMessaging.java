package com.dacs.sict.htxv.taxitranspot.Service;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
public class MyFirebaseMessaging extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(final RemoteMessage remoteMessage) {
        // because this is outside of Main thread, so if you want to run Toast, you need create hander to that
        // Chooser Hander from android .os
        Handler handler = new Handler( Looper.getMainLooper() );
        handler.post( new Runnable() {
            @Override
            public void run() {
                Toast.makeText( MyFirebaseMessaging.this, ""+ remoteMessage.getNotification().getBody(), Toast.LENGTH_SHORT ).show();
            }
        } );


    }
}
