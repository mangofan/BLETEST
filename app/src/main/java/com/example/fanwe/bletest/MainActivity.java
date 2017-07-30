package com.example.fanwe.bletest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
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
    int RSSI_LIMIT = 5, BLE_CHOOSED_NUM = 4,  TIME_INTERVAL = 1000;
    public static Map<String, String> bleNodeLoc = new HashMap<>();    //固定节点的位置Map
    Map<String, Float> bleNodeRssiBias = new HashMap<>();
    Map<String, ArrayList<Double>> mAllRssi = new HashMap<>();    //储存RSSI的MAP
    Map<String, Double> mRssiFilterd = new HashMap<>();     //过滤后的RSSI的Map
    private BleService bleService;
    StringBuffer stringBuffer = new StringBuffer();
    LongSparseArray<String> recentLocationMapRaw = new LongSparseArray<>();
    LongSparseArray<String> locationMapOfOneSec = new LongSparseArray<>();
    int countForInitialize = 0;   //标志是否第一次进入传感器确认函数，如果为第一次，值为零；否则值为1
    String location;
    long lastTimeOfSensor ;
    SensorManager sensorManager;

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

    //传感器事件监听器
    private SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_STEP_DETECTOR:
                    lastTimeOfSensor = Calendar.getInstance().getTimeInMillis();
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
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
        String need = mac + "\n";
        String needMacRssi = "";
        String needVariance = "";
//        Log.e("hh", need);
        double getAvgOfFilterdRssiList = MyUtlis.LogarNormalDistribution(mAllRssi.get(remoteMAC), RSSI_LIMIT);  //获取滤波后的信号强度表和强度平均值
        mRssiFilterd.put(remoteMAC, getAvgOfFilterdRssiList);   //更新MAC地址对应信号强度的map
        if (mRssiFilterd.size() > 1) {
            SparseArray<ArrayList<String>> SortedNodeMacAndRssi = MyUtlis.sortNodeBasedOnRssi(mRssiFilterd, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
            for (int i = 0; i < SortedNodeMacAndRssi.get(1).size(); i++) {
                need += SortedNodeMacAndRssi.get(1).get(i) + " " + SortedNodeMacAndRssi.get(2).get(i) + "\n";
            }

            String locationOnBluetooth = MyUtlis.getMassCenter(SortedNodeMacAndRssi, bleNodeLoc);   //通过质心定位得到位置
            long nowTime = Calendar.getInstance().getTimeInMillis() % 100000;
            recentLocationMapRaw.put(nowTime, locationOnBluetooth);   //将每次蓝牙算出的质心位置的放入map中

            if (countForInitialize == 1) {     //判断是否为第一次进入函数
                locationOnBluetooth = getRecentConfirm(location);
                int flag = MyUtlis.getSensorState(nowTime, lastTimeOfSensor, locationOnBluetooth);
                if(flag == MyUtlis.MOVING){
                    location = locationOnBluetooth;   //如果在运动中，就使用运动中计算出来的
                } else {
                    location = MyUtlis.getStandLocation();   //不在运动中就将这段时间的坐标求平均
                }
            } else {   //第一次进入函数时
                location = locationOnBluetooth;
                countForInitialize = 1;
            }
            need = location + '\n' + need;
            text.setText(need);

            needMacRssi += nowTime + " " + locationOnBluetooth + "  ";
            for (int i = 0; i < SortedNodeMacAndRssi.get(1).size(); i++) {
                needMacRssi += SortedNodeMacAndRssi.get(1).get(i).split(":")[5] + "," + SortedNodeMacAndRssi.get(2).get(i) + "  ";
            }
            for (int i = 0; i < SortedNodeMacAndRssi.get(3).size(); i++) {
                needVariance += SortedNodeMacAndRssi.get(3).get(i) + "  ";
            }

            stringBuffer.append(needMacRssi);
            stringBuffer.append(needVariance);
            stringBuffer.append("\n");

            String Node1 = "19:18:FC:01:F1:0E";
            String Node2 = "19:18:FC:01:F1:0F";
            String Node3 = "19:18:FC:01:F0:F8";
//            String Node4 = "19:18:FC:01:F0:F9";
//            String Node5 = "19:18:FC:01:F0:FD";

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
//            if (mAllRssi.containsKey(Node4)) {
//                ArrayList<Double> nearestNodeList = mAllRssi.get(Node4);
//                String need4 = Node4.split(":")[5] + "\n";
//                for (int i = 0; i < nearestNodeList.size(); i++) {
//                    need4 += nearestNodeList.get(i) + "\n";
//                }
//                text4.setText(need4);
//            }
//            if (mAllRssi.containsKey(Node5)) {
//                ArrayList<Double> nearestNodeList = mAllRssi.get(Node5);
//                String need5 = Node5.split(":")[5] + "\n";
//                for (int i = 0; i < nearestNodeList.size(); i++) {
//                    need5 += nearestNodeList.get(i) + "\n";
//                }
//                text5.setText(need5);
//            }
        }
    }

    //根据最近几次确定的位置，应当排除的情况是定位点出现ABA这种来回的情况时, 要求几秒之内，定位的轨迹应该是一条线，不应该成环，即出现ABA这种情况，这种时候应该过滤掉B
    public String getRecentConfirm(String locationLast) {
        long startTime = recentLocationMapRaw.keyAt(0);  //map中存在的最早的时间
        long endTime = recentLocationMapRaw.keyAt(recentLocationMapRaw.size() - 1);  //map中存在最晚的时间
        long stopTime = endTime - 1000;  //本次工作停止的时间,也是下次开始的时间

        if(stopTime < startTime){
            return locationLast;  //如果整个map中的时间短于1秒，返回map中最新的地址。
        }

        for(int i = 0; recentLocationMapRaw.keyAt(i)<stopTime; i++){  //对map中每个值，向后数一秒，计算这一秒内的位置，存入locationMapOfOneSec
            int thisTimeEndIndex = MyUtlis.searchTimeList(recentLocationMapRaw, i);  //查找本次循环中的结束时间的index
            String loc = MyUtlis.findTheLoc(i, thisTimeEndIndex, recentLocationMapRaw);  //求这段时间内，出现次数最多的位置
            locationMapOfOneSec.put((recentLocationMapRaw.keyAt(i) + TIME_INTERVAL / 2), loc);  //将每次算出来的位置存入map
            recentLocationMapRaw.removeAt(i);
            i--;
        }

        String flag = "flag";
        int count = 0;
        for(int i = 0; i < locationMapOfOneSec.size(); i++){   //map去掉一个元素之后，.size会跟着变化
            if (locationMapOfOneSec.valueAt(i).equals(flag)){
                count += 1;
                if(count > 100){   //当满足100个时，返回此时的flag，确定是位置，并且将此i之前的所有元素删除，维持map不能太长
                    for(int j = i; j > 0; j--){
                        locationMapOfOneSec.removeAt(j);
                    }
                    return flag;
                }
            } else {
                flag = locationMapOfOneSec.valueAt(i);
                count = 1;
                for(int j = i-1 ; j > 0; j--){   //当flag值出现变化时，将之前的节点全部去掉，节省下次进入的遍历花费
                    locationMapOfOneSec.removeAt(j);
                }
                i = 0;
            }
        }
        return locationLast;   //如果不是第一进入（即locationLast不是“first time”），而且（startTime < stopTime），而且locationMapOfOneSec中没有连续持续100次的位置，此时返回locationLast

    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        initBleMap();
        initBluetooth();
        initSensor();
        Intent bindIntent = new Intent(this, BleService.class);
        bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void initSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_GAME);
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
        Log.d(TAG,"know");
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
    protected void onDestroy() {
        super.onDestroy();
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

//    初始化存储节点位置和MAC地址的Map
    private void initBleMap(){
        String location21 = 14.17 + "," + 0.8;
        String location22 = 8.47 + "," + 4.7;
        String location23 = 14.17 + "," + 8.7;
        String location24 = 19.13 + "," + 5.37;

        String location25 = 24 + "," + 14.93;
        String location28 = 16 + "," + 14.93;
        String location37 = 8 + "," + 14.93;
        String location30 = 0 + "," + 14.93;


        bleNodeLoc.put("19:18:FC:01:F1:0E", location21);
//        bleNodeLoc.put("19:18:FC:01:F1:0F", location22);
        bleNodeLoc.put("19:18:FC:01:F0:F8", location23);
//        bleNodeLoc.put("19:18:FC:01:F0:F9", location24);
        bleNodeLoc.put("19:18:FC:01:F0:FA", location25);
        bleNodeLoc.put("19:18:FC:01:F0:FD", location28);
        bleNodeLoc.put("19:18:FC:01:F0:FF", location30);
        bleNodeLoc.put("19:18:FC:00:82:98", location37);



//        bleNodeRssiBias.put("19:18:FC:01:F1:0E", 6f);
//        bleNodeRssiBias.put("19:18:FC:01:F1:0F", 5f);
//        bleNodeRssiBias.put("19:18:FC:01:F0:FD", 2f);
    }
}


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