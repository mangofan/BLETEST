package service;

import android.app.Service;
import android.content.AsyncTaskLoader;
import android.content.Intent;
import android.content.Loader;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.IntDef;

public class BleService extends Service {
    public BleService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        startBackgroundTask(intent, startId);
        return Service.START_STICKY;
        AsyncTaskLoader
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class MyBinder extends Binder{
        public BleService getService(){
            return BleService.this;
        }
    }
    private final IBinder binder = new MyBinder();
}
