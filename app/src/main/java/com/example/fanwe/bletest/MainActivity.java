package com.example.fanwe.bletest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import service.BleService;
import utlis.FileCache;
import utlis.MyUtlis;


public class MainActivity extends AppCompatActivity {
    private static final int ENABLE_BLUETOOTH = 1;
    private static final String TAG = "hh";
    public static BluetoothAdapter bluetoothAdapter;
    TextView text, text1, text2, text3, text4, text5;
    int RSSI_LIMIT = 5, BLE_CHOOSED_NUM = 4;
    public static Map<String, String> bleNodeLoc = new HashMap<>();    //固定节点的位置Map
    Map<String, Float> bleNodeRssiBias = new HashMap<>();
    Map<String, ArrayList<Double>> mAllRssi = new HashMap<>();    //储存RSSI的MAP
    Map<String, Double> mRssiFilterd = new HashMap<>();     //过滤后的RSSI的Map
    private BleService bleService;
    StringBuffer stringBuffer = new StringBuffer();

    //建立Activity和service之间的连接
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //连接时调用,返回一个BleService对象
            bleService = ((BleService.MyBinder) service).getService();
            //注册回调接口来接收蓝牙信息
            bleService.setBleListener(new BleListener() {
                @Override
                public void onBleComing(String mac) {
                    handleMessage(mac);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bleService = null;
        }


    };

    public void handleMessage(String mac) {
        String[] macAndRssi = mac.split(",");
        String remoteMAC = macAndRssi[0];
        Double rssi = Double.valueOf(macAndRssi[1]);

        if (bleNodeRssiBias.containsKey(remoteMAC)) {    //当节点本身信号强度存在误差时，求出平均值后进行修正。
            rssi += bleNodeRssiBias.get(remoteMAC);
        }
        if (mAllRssi.containsKey(remoteMAC)) {    //判断之前是否接收过相同蓝牙节点的广播，分别处理
            ArrayList<Double> list1 = mAllRssi.get(remoteMAC);
            list1.add(0, rssi);   //因为是引用，所以直接修改的是原对象本身
        } else {
            ArrayList<Double> list = new ArrayList<>();
            list.add(rssi);   //如果这个MAC地址没有出现过，建立list存储历次rssi
            mAllRssi.put(remoteMAC, list);
        }
        String need = remoteMAC + " " + rssi + "\n";
        String needMacRssi = "";
        String needVariance = "";
        Log.e("hh", need);
        double getAvgOfFilterdRssiValueList = MyUtlis.LogarNormalDistribution(mAllRssi.get(remoteMAC), RSSI_LIMIT);  //获取滤波后的信号强度表和强度平均值
        mRssiFilterd.put(remoteMAC, getAvgOfFilterdRssiValueList);   //更新MAC地址对应信号强度的map
        if (mRssiFilterd.size() > 2) {
            SparseArray<ArrayList<String>> SortedNodeMacAndRssi = MyUtlis.sortNodeBasedOnRssi(mRssiFilterd, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
            for (int i = 0; i < SortedNodeMacAndRssi.get(1).size(); i++) {
                need += SortedNodeMacAndRssi.get(1).get(i) + " " + SortedNodeMacAndRssi.get(2).get(i) + "\n";
            }
            text.setText(need);
            String locationOnBluetooth = MyUtlis.getMassCenter(SortedNodeMacAndRssi, bleNodeLoc);   //通过质心定位得到位置
            needMacRssi += Calendar.getInstance().getTimeInMillis() % 100000 + " " + locationOnBluetooth + "  ";
            for (int i = 0; i < SortedNodeMacAndRssi.get(1).size(); i++) {
                needMacRssi += SortedNodeMacAndRssi.get(1).get(i).split(":")[5] + "," + SortedNodeMacAndRssi.get(2).get(i) + "  ";
            }
            for (int i = 0; i < SortedNodeMacAndRssi.get(3).size(); i++) {
                needVariance += SortedNodeMacAndRssi.get(3).get(i) + "\t";
            }

            stringBuffer.append(needMacRssi);
            stringBuffer.append(needVariance);
            stringBuffer.append("\n");


            String Node1 = "19:18:FC:01:F0:FD";
            String Node2 = "19:18:FC:01:F0:FC";
            String Node3 = "19:18:FC:01:F0:FE";
            String Node4 = "19:18:FC:01:F1:0E";
            String Node5 = "19:18:FC:01:F1:0F";
            if (mAllRssi.containsKey(Node1)) {
                ArrayList<Double> nearestNodeList = mAllRssi.get(Node1);
                String need1 = Node1.split(":")[5] + "\n";
                for (int i = 0; i < nearestNodeList.size(); i++) {
                    need1 += nearestNodeList.get(i) + "\n";
                }
                text1.setText(need1);
            }
            if (mAllRssi.containsKey(Node2)) {
                ArrayList<Double> nearestNodeList = mAllRssi.get(Node2);
                String need2 = Node2.split(":")[5] + "\n";
                for (int i = 0; i < nearestNodeList.size(); i++) {
                    need2 += nearestNodeList.get(i) + "\n";
                }
                text2.setText(need2);
            }
            if (mAllRssi.containsKey(Node3)) {
                ArrayList<Double> nearestNodeList = mAllRssi.get(Node3);
                String need3 = Node3.split(":")[5] + "\n";
                for (int i = 0; i < nearestNodeList.size(); i++) {
                    need3 += nearestNodeList.get(i) + "\n";
                }
                text3.setText(need3);
            }
            if (mAllRssi.containsKey(Node4)) {
                ArrayList<Double> nearestNodeList = mAllRssi.get(Node4);
                String need4 = Node4.split(":")[5] + "\n";
                for (int i = 0; i < nearestNodeList.size(); i++) {
                    need4 += nearestNodeList.get(i) + "\n";
                }
                text4.setText(need4);
            }
            if (mAllRssi.containsKey(Node5)) {
                ArrayList<Double> nearestNodeList = mAllRssi.get(Node5);
                String need5 = Node5.split(":")[5] + "\n";
                for (int i = 0; i < nearestNodeList.size(); i++) {
                    need5 += nearestNodeList.get(i) + "\n";
                }
                text5.setText(need5);
            }
        }
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        initBleMap();
        initBluetooth();
        Intent bindIntent = new Intent(this, BleService.class);
        bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void initBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            //蓝牙未打开，提醒用户打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, ENABLE_BLUETOOTH);
        }else{
            startService(new Intent(this, BleService.class));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            startService(new Intent(this, BleService.class));
        }
    }

    public void initView() {
        text = (TextView) findViewById(R.id.textView);
        text1 = (TextView) findViewById(R.id.textView1);
        text2 = (TextView) findViewById(R.id.textView2);
        text3 = (TextView) findViewById(R.id.textView3);
        text4 = (TextView) findViewById(R.id.textView4);
        text5 = (TextView) findViewById(R.id.textView5);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                FileCache.saveFile(stringBuffer + "\n");  //位置输出到文件中。
            }
        });
        thread.start();
        unbindService(mConnection);
        stopService(new Intent(this, BleService.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

//    初始化存储节点位置和MAC地址的Map
    private void initBleMap(){
        String location21 = 11.5 + "," + 0.7;
        String location22 = 15.8 + "," + 0.7;
        String location23 = 7.8 + "," + 4.7;
        String location24 = 11.8 + "," + 4.7;
        String location25 = 15.8 + "," + 4.7;
        String location26 = 19.8 + "," + 4.7;
        String location27 = 7.8 + "," + 8.7;
        String location28 = 11.8 + "," + 8.7;
        String location29 = 15.8 + "," + 8.7;
        String location30 = 19.8 + "," + 8.7;

        bleNodeLoc.put("19:18:FC:01:F1:0E", location21);
        bleNodeLoc.put("19:18:FC:01:F1:0F", location22);
        bleNodeLoc.put("19:18:FC:01:F0:F8", location23);
        bleNodeLoc.put("19:18:FC:01:F0:F9", location24);
        bleNodeLoc.put("19:18:FC:01:F0:FA", location25);
        bleNodeLoc.put("19:18:FC:01:F0:FB", location26);
        bleNodeLoc.put("19:18:FC:01:F0:FC", location27);
        bleNodeLoc.put("19:18:FC:01:F0:FD", location28);
        bleNodeLoc.put("19:18:FC:01:F0:FE", location29);
        bleNodeLoc.put("19:18:FC:01:F0:FF", location30);

//        bleNodeRssiBias.put("19:18:FC:01:F1:0E", 6f);
//        bleNodeRssiBias.put("19:18:FC:01:F1:0F", 5f);
//        bleNodeRssiBias.put("19:18:FC:01:F0:FD", 2f);
    }
}


//

//
//    @Override
//    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
//        return super.bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
//    }

//        if (android.os.Build.VERSION.SDK_INT >= 23) {    //版本大于6.0时，需要申请位置权限，并且打开位置功能。
//            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
//                    android.Manifest.permission.ACCESS_FINE_LOCATION}, 2);   //申请位置权限
//            if (isLocationOpen(getApplicationContext())) {
//                Intent enableLocate = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
//                startActivityForResult(enableLocate, ENABLE_LOCATION);   //打开位置功能
//            }
//        }

//判断位置信息是否开启
//    public static boolean isLocationOpen(final Context context){
//        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
//        //gps定位
//        boolean isGpsProvider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
//        //网络定位
//        boolean isNetWorkProvider = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
//        return isGpsProvider|| isNetWorkProvider;
//    }

//    TextView text,text1,text2,text3,text4,text5;
//    double count = 0;
//
//    int RSSI_LIMIT = 5, BLE_CHOOSED_NUM = 4;
//    ScheduledFuture<?> future;
//    private static final int ENABLE_BLUETOOTH = 1;
//    Map<String, double[]> bleNodeLoc = new HashMap<>();    //固定节点的位置Map
//    Map<String, Float> bleNodeRssiBias = new HashMap<>();
//    Map<String, ArrayList<Double>> mAllRssi = new HashMap<>();    //储存RSSI的MAP
//    Map<String, Double> mRssiFilterd = new HashMap<>();     //过滤后的RSSI的Map
//    private static final String TAG = "hh";
//    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//        text = (TextView)findViewById(R.id.textView);
//        text1 = (TextView)findViewById(R.id.textView1);
//        text2 = (TextView)findViewById(R.id.textView2);
//        text3 = (TextView)findViewById(R.id.textView3);
//        text4 = (TextView)findViewById(R.id.textView4);
//        text5 = (TextView)findViewById(R.id.textView5);
//
//        initBleMap();
//        initBluetooth();
//    }
//
//
//    //蓝牙广播监听器
//    public BroadcastReceiver mReceiver = new BroadcastReceiver() {
//        String dFinished = BluetoothAdapter.ACTION_DISCOVERY_FINISHED;
//        String dStart = BluetoothAdapter.ACTION_DISCOVERY_STARTED;
//
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            Log.e("hh","receive");
//            if(dStart.equals(intent.getAction())){
//                Log.e(TAG, "discovery start");
//            }
//            BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//            String remoteMAC;
//            double rssi;
//            if (remoteDevice != null) {    //判断接受到的信息中设备是否为空
//                remoteMAC = remoteDevice.getAddress();
//                count ++;
//                String need = remoteMAC + "," + count;
//                text.setText(need);
//                Log.d(TAG, need);
////                if (bleNodeLoc.containsKey(remoteMAC)) {   //判断接受到的蓝牙节点是否在已知的蓝牙节点map中
////                    rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
////                    if(bleNodeRssiBias.containsKey(remoteMAC)){    //当节点本身信号强度存在误差时，求出平均值后进行修正。
////                        rssi += bleNodeRssiBias.get(remoteMAC);
////                    }
////                    Log.d(TAG,"have rssi");
////                    if (!dFinished.equals(intent.getAction())) {   //接收到的事件不是结束时
////                        if (mAllRssi.containsKey(remoteMAC)) {    //判断之前是否接收过相同蓝牙节点的广播，分别处理
////                            ArrayList<Double> list1 = mAllRssi.get(remoteMAC);
////                            list1.add(0, rssi);   //因为是引用，所以直接修改的是原对象本身
////                            Log.d(TAG,"have mac before");
////                        } else {
////                            ArrayList<Double> list = new ArrayList<>();
////                            list.add((double) rssi);   //如果这个MAC地址没有出现过，建立list存储历次rssi
////                            mAllRssi.put(remoteMAC, list);
////                            Log.d(TAG,"dont have mac before");
////                        }
////                        String need = remoteMAC + " " + rssi + "\n";
////                        Log.e("hh", need);
////                        double getAvgOfFilterdRssiValueList = MyUtlis.LogarNormalDistribution(mAllRssi.get(remoteMAC), RSSI_LIMIT);  //获取滤波后的信号强度表和强度平均值
////
////                        Log.d(TAG,"get avg");
////                        mRssiFilterd.put(remoteMAC, getAvgOfFilterdRssiValueList);   //更新MAC地址对应信号强度的map
////                        if (mRssiFilterd.size() > 2) {
////                            SparseArray<ArrayList<String>> SortedNodeMacAndRssi = MyUtlis.sortNodeBasedOnRssi(mRssiFilterd, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
////                            for(int i = 0; i < SortedNodeMacAndRssi.get(1).size(); i++){
////                                need += SortedNodeMacAndRssi.get(1).get(i) + " " + SortedNodeMacAndRssi.get(2).get(i) + "\n";
////                            }
////                            text.setText(need);
//////                            String nearestNode = SortedNodeMacAndRssi.get(1).get(0);
////
////                            String Node1 = "19:18:FC:01:F0:FD";
////                            String Node2 = "19:18:FC:01:F0:FC";
////                            String Node3 = "19:18:FC:01:F0:F9";
////                            String Node4 = "19:18:FC:01:F0:FE";
////                            String Node5 = "19:18:FC:01:F0:FD";
////                            if(mAllRssi.containsKey(Node1)){
////                                ArrayList<Double> nearestNodeList = mAllRssi.get(Node1);
////                                String need1 = Node1.split(":")[5] + "\n";
////                                for(int i = 0; i < nearestNodeList.size(); i++){
////                                    need1 += nearestNodeList.get(i) + "\n";
////                                }
////                                text1.setText(need1);
////                            }
////                            if(mAllRssi.containsKey(Node2)){
////                                ArrayList<Double> nearestNodeList = mAllRssi.get(Node2);
////                                String need2 = Node2.split(":")[5] + "\n";
////                                for(int i = 0; i < nearestNodeList.size(); i++){
////                                    need2 += nearestNodeList.get(i) + "\n";
////                                }
////                                text2.setText(need2);
////                            }
////                            if(mAllRssi.containsKey(Node3)){
////                                ArrayList<Double> nearestNodeList = mAllRssi.get(Node3);
////                                String need3 = Node3.split(":")[5] + "\n";
////                                for(int i = 0; i < nearestNodeList.size(); i++){
////                                    need3 += nearestNodeList.get(i) + "\n";
////                                }
////                                text3.setText(need3);
////                            }
////                            if(mAllRssi.containsKey(Node4)){
////                                ArrayList<Double> nearestNodeList = mAllRssi.get(Node4);
////                                String need4 = Node4.split(":")[5] + "\n";
////                                for(int i = 0; i < nearestNodeList.size(); i++){
////                                    need4 += nearestNodeList.get(i) + "\n";
////                                }
////                                text4.setText(need4);
////                            }
////                            if(mAllRssi.containsKey(Node5)){
////                                ArrayList<Double> nearestNodeList = mAllRssi.get(Node5);
////                                String need5 = Node5.split(":")[5] + "\n";
////                                for(int i = 0; i < nearestNodeList.size(); i++){
////                                    need5 += nearestNodeList.get(i) + "\n";
////                                }
////                                text5.setText(need5);
////                            }
////                            Log.d(TAG, "rssi > 2");
////                        }
////                    }
////                }else{
////                    Log.d(TAG, "not in map");
////                }
//            }
//            else{
//                Log.d(TAG, "device null");
//            }
//        }
//    };
//
//    //提示用户开启手机蓝牙
//    public void initBluetooth() {
//        if (android.os.Build.VERSION.SDK_INT >= 23){
//            ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
//                    android.Manifest.permission.ACCESS_FINE_LOCATION},2);
//
//        }
//        if (!bluetoothAdapter.isEnabled()) {
//            //蓝牙未打开，提醒用户打开
//            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(intent, ENABLE_BLUETOOTH);
//        }
//
//
//        startDiscovery();
//    }
//
//

//
////    @Override
////    protected void onPause() {
////        bluetoothAdapter.cancelDiscovery();
////        super.onPause();
////    }
//
////    @Override
////    protected void onResume() {
////        super.onResume();
//////        bluetoothAdapter.startDiscovery();
////    }
//
//    @Override
//    protected void onDestroy() {
////        EventBus.getDefault().unregister(this);
////        cancelTask();
//        bluetoothAdapter.cancelDiscovery();
//        super.onDestroy();
//    }
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void startDiscovery() {
//        registerReceiver(mReceiver, new IntentFilter((BluetoothDevice.ACTION_FOUND)));
//        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
////        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
//        if (bluetoothAdapter.isEnabled() && !bluetoothAdapter.isDiscovering()) {
//            bluetoothAdapter.startDiscovery();
//        }
//
////        cancelTask();
//        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
//        future = executorService.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//
//                bluetoothAdapter.startDiscovery();
//                Log.d(TAG,"start discovery");
//            }
//        }, 5, 10, TimeUnit.SECONDS);
//    }
//    public void cancelTask() {
//        if (future != null) {
//            future.cancel(false);
//            future = null;
//        }
//    }