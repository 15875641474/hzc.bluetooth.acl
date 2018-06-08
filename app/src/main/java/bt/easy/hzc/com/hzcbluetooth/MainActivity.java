package bt.easy.hzc.com.hzcbluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.hzc.easy.bluetooth.HzcBluetoothAclService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    static final String tag = "MainActivity";

    ListView list_view;
    ArrayAdapter<MyDevice> adapter;
    List<MyDevice> lists = new ArrayList<>();
    MyDevice device;
    TextView tv_device_info, tv_msg;

    class MyDevice {

        public MyDevice(BluetoothDevice device) {
            this.device = device;
        }

        private BluetoothDevice device;
        private BluetoothSocket socket;

        public void setDevice(BluetoothDevice device) {
            this.device = device;
        }

        public BluetoothSocket getSocket() {
            return socket;
        }

        public void setSocket(BluetoothSocket socket) {
            this.socket = socket;
        }

        public BluetoothDevice getDevice() {
            return device;
        }

        @Override
        public String toString() {
            String name = device.getAddress();
            if (!TextUtils.isEmpty(device.getName())) {
                return device.getName();
            }
            return name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        list_view = (ListView) findViewById(R.id.list_view);
        tv_device_info = (TextView) findViewById(R.id.tv_device_info);
        tv_msg = (TextView) findViewById(R.id.tv_msg);
        adapter = new ArrayAdapter<MyDevice>(this, android.R.layout.simple_list_item_1, lists);
        list_view.setAdapter(adapter);
        //设置当前选中的设备
        list_view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                device = lists.get(position);
                tv_device_info.setText(device.toString());
            }
        });
        //初始化
        HzcBluetoothAclService.getInstance().init(this);
        //设置搜索到设备时的事件
        HzcBluetoothAclService.getInstance().setOnFindDeviceListence(new HzcBluetoothAclService.OnFindDeviceListence() {
            @Override
            public void onFindDevice(BluetoothDevice bluetoothDevice) {
                lists.add(new MyDevice(bluetoothDevice));
                adapter.notifyDataSetChanged();
            }
        });
        //搜索
        findViewById(R.id.btn_find).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!HzcBluetoothAclService.getInstance().isScaning())
                    lists.clear();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
                HzcBluetoothAclService.getInstance().doScanBtDevice(null);
                setTitle(String.format("%s.%s",HzcBluetoothAclService.getInstance().getmBtAdapter().getName(),HzcBluetoothAclService.getInstance().getmBtAdapter().getAddress()));
            }
        });
        //链接
        findViewById(R.id.btn_connection).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HzcBluetoothAclService.getInstance().doConnection(device.getDevice(), new HzcBluetoothAclService.OnConnectionServiceListence() {
                    @Override
                    public void onConnecting() {
                        setMsg("连接中.");
                    }

                    @Override
                    public void onSuccess(BluetoothSocket socket) {
                        setMsg("获得服务端socket");
                        device.setSocket(socket);
                    }

                    @Override
                    public void onError(String message) {
                        setMsg(message);
                    }
                });

            }
        });
        //开启蓝牙集群服务
        findViewById(R.id.btn_start_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HzcBluetoothAclService.getInstance().startBtConnectonService("btwt_" + v.hashCode());
                setTitle(String.format("%s.%s","btwt_" + v.hashCode(),HzcBluetoothAclService.getInstance().getmBtAdapter().getAddress()));
            }
        });
        //断开链接
        findViewById(R.id.btn_disconnection).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (device != null && device.getSocket() != null) {
                    try {
                        device.getSocket().close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        //停止蓝牙集群服务
        findViewById(R.id.btn_stop_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HzcBluetoothAclService.getInstance().doStopBtConnectionService();
            }
        });

        //设置新的客户端接入事件
        HzcBluetoothAclService.getInstance().setOnBtServiceListence(new HzcBluetoothAclService.OnBtServiceListence() {
            @Override
            public void onStarting() {
                setMsg("蓝牙集群接收中");
            }

            @Override
            public void onNewConnection(BluetoothSocket bluetoothSocket) {
                setMsg(String.format("有新的客户端接入%s total=%d", bluetoothSocket.getRemoteDevice().getName(), HzcBluetoothAclService.getInstance().getClientSocketMap().size()));
                MyDevice myDevice = new MyDevice(bluetoothSocket.getRemoteDevice());
                myDevice.setSocket(bluetoothSocket);
                lists.add(myDevice);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                setMsg(msg);
            }

            @Override
            public void onStop() {
                setMsg("服务已停止");
            }
        });

        HzcBluetoothAclService.getInstance().setOnConnectionListence(new HzcBluetoothAclService.OnConnectionListence() {
            @Override
            public void onConnectioned(BluetoothDevice device) {
                setMsg("已经链接设备" + device.getName());
            }

            @Override
            public void onDisconnected(BluetoothDevice device) {
                setMsg("已经断开链接" + device.getName());
            }
        });

    }

    private void setMsg(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_msg.setText(msg);
            }
        });
    }
}
