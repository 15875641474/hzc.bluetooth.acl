package com.hzc.easy.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by huangzongcheng on 2018/6/4.14:49
 * 328854225@qq.com
 */
public class HzcBluetoothAclService {
    //默认蓝牙通道UUID
    private static final String BTUUID = "00001101-0000-1000-8000-00805F9B34FB";
    private static final String tag = "HzcBluetoothAclService";
    private static HzcBluetoothAclService hzcBluetoothAclService = Inner.hzcBluetoothAclService;
    private boolean scaning, btWaitConnecting, autoBond, enableRetryScanDevice = true, inited;
    private int retrySize = 4;
    private String sdpName = "HZC.AclTtSdpService";

    private Executor threadPool = Executors.newCachedThreadPool();
    private BluetoothServerSocket serverSoctet;
    private BluetoothAdapter mBtAdapter;
    private Activity activity;
    private Map<String, BluetoothSocket> clientSocketMap = new HashMap<>();


    private BtBoradcaseReceiver btBoradcaseReceiver;
    private OnScanListence onScanListence;
    private OnRequestBoundListence onRequestBoundListence;
    private OnConnectionStatusListence onConnectionStatusListence;
    private List<OnBluetoothEnableListence> onBluetoothEnableListenceList = new ArrayList<>();
    private OnBtServiceListence onBtServiceListence;

    private void HzcBluetoothService() {

    }

    /**
     * 设置重试操作次数
     *
     * @param retrySize
     */
    public void setRetrySize(int retrySize) {
        this.retrySize = retrySize;
    }

    public void setSdpName(String sdpName) {
        this.sdpName = sdpName;
    }

    /**
     * 是否启动重试搜索
     *
     * @param enableRetryScanDevice
     */
    public void setEnableRetryScanDevice(boolean enableRetryScanDevice) {
        this.enableRetryScanDevice = enableRetryScanDevice;
    }

    /**
     * 默认空的接口代理
     */
    class PublicNullProxyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return "";
        }
    }

    /**
     * 返回一个被代理实现的接口类
     *
     * @param cls
     * @param <T>
     * @return
     */
    private <T> T getNullImpl(Class cls) {
        return (T) Proxy.newProxyInstance(activity.getClass().getClassLoader(), new Class[]{cls}, new PublicNullProxyHandler());
    }

    public static HzcBluetoothAclService getInstance() {
        return hzcBluetoothAclService;
    }

    /**
     * 单例返回
     */
    static class Inner {
        static HzcBluetoothAclService hzcBluetoothAclService;

        static {
            hzcBluetoothAclService = new HzcBluetoothAclService();
        }
    }

    /**
     * 断开客户端链接
     *
     * @param mac
     */
    public void doDisConnectionClient(String mac) {
        BluetoothSocket soctet = clientSocketMap.get(mac);
        if (soctet != null) {
            try {
                soctet.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 是否搜索中
     *
     * @return
     */
    public boolean isScaning() {
        return scaning;
    }

    /**
     * 初始化
     *
     * @param activity
     */
    public void init(Activity activity) {
        if (inited)
            return;
        this.activity = activity;
        hzcBluetoothAclService.mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        initBoradcase();
        inited = true;
    }

    /**
     * 注册广播事件监听
     */
    private void initBoradcase() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);

        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_CLASS_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.setPriority(1000);
        btBoradcaseReceiver = new BtBoradcaseReceiver();
        this.activity.registerReceiver(btBoradcaseReceiver, filter);
    }

    /**
     * 关闭服务
     */
    public void doStopBtConnectionService() {
        if (btWaitConnecting) {
            btWaitConnecting = false;
            try {
                serverSoctet.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!clientSocketMap.isEmpty()) {
            for (String key : clientSocketMap.keySet()) {
                try {
                    clientSocketMap.get(key).close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clientSocketMap.remove(key);
            }
        }
    }

    /**
     * 开启蓝牙链接服务
     */
    public void startBtConnectonService(final String displayName) {
        //多少秒内允许蓝牙是可见的
        setDiscoverableTimeout(60 * 60 * 24);
        //这个可见事件最大值是300秒
//        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
//        activity.startActivity(discoverableIntent);

        if (btWaitConnecting) {
            return;
        }
        //启动蓝牙
        if (!isBluetoothEnabled()) {
            OnBluetoothEnableListence l1 = new OnBluetoothEnableListence() {
                @Override
                public void onEnable() {
                    onBluetoothEnableListenceList.remove(this);
                    startBtConnectonService(displayName);
                }

                @Override
                public void onDisable() {

                }
            };
            addOnBluetoothEnableListence(l1);
            //启动蓝牙，这个时候会进入异步线程启动，成功后会广播消息
            doEnableBt(true);
        }
        mBtAdapter.setName(displayName);
        btWaitConnecting = true;
        //客户端接入线程
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (onBtServiceListence == null) {
                        onBtServiceListence = getNullImpl(OnBtServiceListence.class);
                    }
                    onBtServiceListence.onStarting();
                    serverSoctet = mBtAdapter.listenUsingRfcommWithServiceRecord(sdpName, java.util.UUID.fromString(BTUUID));
                    //每获得一个循环，表示有一台新的机器在尝试链接
                    while (btWaitConnecting) {
                        BluetoothSocket soctet = serverSoctet.accept();
                        clientSocketMap.put(soctet.getRemoteDevice().getAddress(), soctet);
                        onBtServiceListence.onNewConnection(soctet);
                    }
                    onBtServiceListence.onStop();
                } catch (Exception e) {
                    Log.i(tag, e.toString());
                    onBtServiceListence.onError(e.toString());
                }
            }
        });
    }

    /**
     * 设置蓝牙可见时间
     *
     * @param secound(秒)
     */
    public void setDiscoverableTimeout(int secound) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        try {
            Method setDiscoverableTimeout = BluetoothAdapter.class.getMethod("setDiscoverableTimeout", int.class);
            setDiscoverableTimeout.setAccessible(true);
            Method setScanMode = BluetoothAdapter.class.getMethod("setScanMode", int.class, int.class);
            setScanMode.setAccessible(true);
            setDiscoverableTimeout.invoke(adapter, secound);
            setScanMode.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, secound);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 反初始化
     */
    public void unInit() {
        if (!inited)
            return;
        this.activity.unregisterReceiver(btBoradcaseReceiver);
        //停止链接服务
        btWaitConnecting = false;
        //清空客户端
        if (clientSocketMap.size() > 0) {
            for (String mac : clientSocketMap.keySet()) {
                try {
                    clientSocketMap.get(mac).close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            clientSocketMap.clear();
        }
        //设置蓝牙不可见
        setDiscoverableTimeout(1);
        if (mBtAdapter != null) {
            mBtAdapter.disable();
            mBtAdapter = null;
        }
        this.activity = null;
        inited = false;
    }

    /**
     * 启动关闭蓝牙
     */
    public boolean doEnableBt(boolean enable) {
        if (enable) {
            enable = mBtAdapter.enable();
        } else {
            enable = mBtAdapter.disable();
        }
        return enable;
    }

    /**
     * 开启蓝牙搜索
     */
    public void doScanBtDevice() {
        doScanBtDevice(0);
    }

    /**
     * 开启蓝牙搜索
     *
     * @param retryIndex 重试3次
     */
    private void doScanBtDevice(final int retryIndex) {
        if (onScanListence == null) {
            onScanListence = getNullImpl(OnScanListence.class);
        }
        //超过4次不再重试搜索，发送失败通知
        if (retryIndex > (enableRetryScanDevice ? retrySize : 1)) {
            onScanListence.onError();
            return;
        }
        //蓝牙状态监听事件
        OnBluetoothEnableListence l1 = new OnBluetoothEnableListence() {
            @Override
            public void onEnable() {
                Log.i(tag, "retry scan device");
                onBluetoothEnableListenceList.remove(this);
                doScanBtDevice(retryIndex + 1);
            }

            @Override
            public void onDisable() {
                onEnable();
            }
        };
        //启动蓝牙
        if (!isBluetoothEnabled()) {
            addOnBluetoothEnableListence(l1);
            doEnableBt(true);
            return;
        }
        //启动搜索
        if (mBtAdapter.startDiscovery()) {
            Log.i(tag, "scan success");
            onScanListence.onSuccess();
            return;
        }
        //启动重试机制
        if (enableRetryScanDevice) {
            addOnBluetoothEnableListence(l1);
            doEnableBt(false);
            onScanListence.onRetry(retryIndex + 1);
            Log.i(tag, "retry enable scan device for " + retryIndex);
            return;
        }
    }

    /**
     * 是否启用了蓝牙
     *
     * @return
     */
    public boolean isBluetoothEnabled() {
        return mBtAdapter.isEnabled();
    }

    /**
     * 是否已进行绑定
     *
     * @param device
     * @return
     */
    public boolean isBondDevice(BluetoothDevice device) {
        return device.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    /**
     * 取消搜索
     *
     * @return
     */
    public boolean doCancelScan() {
        return mBtAdapter.cancelDiscovery();
    }

    /**
     * 链接设备
     *
     * @param device
     * @param listence
     */
    public void doConnection(final BluetoothDevice device, OnConnectionServiceListence listence) {
        doCancelScan();
        if (listence == null) {
            listence = getNullImpl(OnConnectionServiceListence.class);
        }
        final OnConnectionServiceListence onConnectionServiceListence = listence;
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                onConnectionServiceListence.onConnecting();
                //绑定并链接
                try {
                    if (doBondDevice(device)) {
                        BluetoothSocket socket = doConnectionWithUUID(device);
                        if (socket != null && socket.isConnected()) {
                            onConnectionServiceListence.onSuccess(socket);
                        }
                    }
                } catch (Exception e) {
                    Log.i(tag, e.toString());
                    onConnectionServiceListence.onError(e.toString());
                }
            }
        });
    }

    /**
     * 绑定蓝牙设备
     *
     * @param device
     */
    private boolean doBondDevice(BluetoothDevice device) {
        boolean bool = isBondDevice(device);
        if (!bool) {
            try {
                /**
                 //采用这个部分机型可能不会发起配对
                 device.createBond()
                 */
                //能保证能发起配对请求，成功后，取消链接来达到绑定的目的
                Method m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                BluetoothSocket socket = (BluetoothSocket) m.invoke(device, 1);
                socket.connect();
                if (socket.isConnected())
                    socket.close();
                return true;
            } catch (Exception e) {
                bool = isBondDevice(device);
            }
        }
        return bool;
    }

    /**
     * 以服务器形式链接
     *
     * @param device
     * @return
     */
    private BluetoothSocket doConnectionWithUUID(BluetoothDevice device) throws IOException {
        BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(BTUUID));
        socket.connect();
        return socket;
    }

    /**
     * 接收广播事件
     */
    class BtBoradcaseReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(tag, intent.getAction());
            switch (intent.getAction()) {
                //开启搜索
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED: {
                    scaning = true;
                    if (onScanListence != null) {
                        onScanListence.onScanStarted();
                    }
                }
                break;
                //搜索完成
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED: {
                    scaning = false;
                    if (onScanListence != null) {
                        onScanListence.onDiscoveryFinished();
                    }
                }
                break;
                //搜索到设备
                case BluetoothDevice.ACTION_FOUND: {
                    scaning = true;
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (onScanListence != null) {
                        onScanListence.onFindDevice(device);
                    }
                }
                break;
                //请求绑定事件监听
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                    if (onRequestBoundListence != null) {
                        switch (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                            case BluetoothDevice.BOND_NONE: {
                                onRequestBoundListence.boundFailed();
                            }
                            break;
                            case BluetoothDevice.BOND_BONDING: {
                                onRequestBoundListence.boundRequesting();
                            }
                            break;
                            case BluetoothDevice.BOND_BONDED: {
                                onRequestBoundListence.boundSuccess();
                            }
                            break;
                        }
                    }
                }
                break;
                //已链接
                case BluetoothDevice.ACTION_ACL_CONNECTED: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (onConnectionStatusListence != null) {
                        onConnectionStatusListence.onConnectioned(device);
                    }
                }
                break;
                //断开链接
                case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (onConnectionStatusListence != null) {
                        onConnectionStatusListence.onDisconnected(device);
                    }
                    clientSocketMap.remove(device.getAddress());
                }
                break;
                //配对中
                case BluetoothDevice.ACTION_PAIRING_REQUEST: {
                    //启用自动配对
                    if (autoBond) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        try {
                            //如果没有将广播终止，则会出现一个一闪而过的配对框。
                            abortBroadcast();
                            //3.调用setPin方法进行配对...
                            ClsUtils.setPin(device.getClass(), device, "123456");
//                            ClsUtils.setPairingConfirmation(BluetoothDevice.class,device,true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
                //蓝牙模块状态
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    Log.i(tag, String.format("ACTION_STATE_CHANGED=%d", intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)));
                    switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        case BluetoothAdapter.STATE_OFF: {//关闭
                            for (int i = 0; i < onBluetoothEnableListenceList.size(); i++) {
                                onBluetoothEnableListenceList.get(i).onDisable();
                            }
                        }
                        break;
                        case BluetoothAdapter.STATE_ON: {//启动
                            for (int i = 0; i < onBluetoothEnableListenceList.size(); i++) {
                                onBluetoothEnableListenceList.get(i).onEnable();
                            }
                        }
                        break;
                    }
                }
                break;
                //链接状态改变
                case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED: {

                }
                break;
            }
        }
    }

    /**
     * 有问题
     * 当接收到ACTION_PAIRING_REQUEST广播之前
     * 系统会广播一个ACTION_ACL_CONNECTED已连接的通知
     * 但是这个ACTION_ACL_CONNECTED属于PIN码的配对请求链接
     * 只要一直处于请求配对状态中，那么目前设备的状态就是链接中
     * 但是实际上，socket是依然没有建立的
     * 又因为调用了abortBroadcast()，所以配对请求的窗口会被拦截掉，导致在配对PIN默认显示时间内，一直处于连接中
     *
     * @param autoBond
     */
    @Deprecated
    public void setAutoBond(boolean autoBond) {
        this.autoBond = autoBond;
    }

    public BluetoothAdapter getmBtAdapter() {
        return mBtAdapter;
    }

    public Map<String, BluetoothSocket> getClientSocketMap() {
        return clientSocketMap;
    }

    public void setOnRequestBoundListence(OnRequestBoundListence onRequestBoundListence) {
        this.onRequestBoundListence = onRequestBoundListence;
    }

    public void setOnScanListence(OnScanListence onScanListence) {
        this.onScanListence = onScanListence;
    }

    public void setOnBtServiceListence(OnBtServiceListence onBtServiceListence) {
        this.onBtServiceListence = onBtServiceListence;
    }

    public void setOnConnectionStatusListence(OnConnectionStatusListence onConnectionStatusListence) {
        this.onConnectionStatusListence = onConnectionStatusListence;
    }

    public void addOnBluetoothEnableListence(OnBluetoothEnableListence listence) {
        onBluetoothEnableListenceList.add(listence);
    }

    /**
     * 搜索状态事件监听
     */
    public interface OnScanListence {
        void onSuccess();

        void onError();

        void onRetry(int index);

        void onScanStarted();

        void onFindDevice(BluetoothDevice bluetoothDevice);

        void onDiscoveryFinished();
    }

    /**
     * 绑定配对事件监听
     */
    public interface OnRequestBoundListence {
        void boundFailed();

        void boundRequesting();

        void boundSuccess();
    }

    /**
     * 链接状态监听
     */
    public interface OnConnectionStatusListence {
        void onConnectioned(BluetoothDevice device);

        void onDisconnected(BluetoothDevice device);
    }

    /**
     * 蓝牙启动事件监听
     */
    public interface OnBluetoothEnableListence {
        void onEnable();

        void onDisable();
    }

    /**
     * 蓝牙服务事件监听
     */
    public interface OnBtServiceListence {

        void onStarting();

        void onNewConnection(BluetoothSocket bluetoothSocket);

        void onError(String msg);

        void onStop();
    }

    /**
     * 链接服务监听事件
     */
    public interface OnConnectionServiceListence {
        void onConnecting();

        void onSuccess(BluetoothSocket socket);

        void onError(String message);
    }

}
