package service;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.example.fanwe.bletest.BleListener;
import com.example.fanwe.bletest.MainActivity;

import java.util.ArrayList;
import java.util.List;


public class BleService extends Service {


    BluetoothLeScanner bluetoothLeScanner;
    private static final String TAG = "hh";
    private BleListener bleListener;

    public void setBleListener(BleListener bleListener){
        this.bleListener = bleListener;
    }

    public BleService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        startBackgroundTask(intent, startId);
        BleThread bleThread = new BleThread();
        bleThread.start();
        return Service.START_STICKY;
    }

    private class BleThread extends Thread{
        @Override
        public void run() {
            super.run();
            initScan();
        }
    }

    public void initScan(){
        bluetoothLeScanner = MainActivity.bluetoothAdapter.getBluetoothLeScanner();
        bluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), leCallback);
    }

    public ScanCallback leCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if(bleListener != null) {
                int rssi = result.getRssi();
                BluetoothDevice device = result.getDevice();
                if (device != null) {
                    String mac = device.getAddress();
                    if (MainActivity.bleNodeLoc.containsKey(mac)) {
                        String macRssi = mac + "," + rssi;
                        bleListener.onBleComing(macRssi);
                    }
                }
            }
        }
    };

    private List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter.Builder builder = new ScanFilter.Builder();
        return scanFilters;
    }


    public ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        return builder.build();
    }

    private final IBinder binder = new MyBinder();
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
//
    public class MyBinder extends Binder{
        public BleService getService(){
            return BleService.this;
        }
    }

}
