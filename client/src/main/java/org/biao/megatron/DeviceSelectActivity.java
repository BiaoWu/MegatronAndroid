package org.biao.megatron;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.biao.badapter.BAdapter;
import com.biao.badapter.BDataSource;
import com.biao.badapter.OnItemClickListener;
import com.biao.badapter.itemdelegate.simple.BSimpleItemDelegate;
import com.biao.badapter.itemdelegate.simple.BSimpleViewHolder;
import java.lang.reflect.Method;
import java.util.Set;

public class DeviceSelectActivity extends AppCompatActivity {
  public static final String RESULT_BLUETOOTH_DEVICE = "bluetooth_device";

  private static final String TAG = DeviceSelectActivity.class.getSimpleName();
  private static final boolean debug = BuildConfig.DEBUG;

  private static final int request_action_discovering = 52066;
  private static final int request_find_paired = 52660;

  private BAdapter adapter;
  private BDataSource<BluetoothDevice> dataSource = new BDataSource<>();

  private BroadcastReceiver receiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (debug) Log.d(TAG, "action -> " + action);

      BluetoothDevice device = null;
      switch (action) {
        case BluetoothDevice.ACTION_FOUND:
        case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
          device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
          break;
      }
      if (device != null) {
        int i = dataSource.getDataList().indexOf(device);
        if (i < 0) {
          dataSource.add(device);
          adapter.notifyItemInserted(dataSource.size() - 1);
        } else {
          adapter.notifyItemChanged(i);
        }
      }
    }
  };

  private Toolbar toolbar;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_device_select);
    setupToolbar();
    findPaired();
    setupRv();

    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothDevice.ACTION_FOUND);
    filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
    registerReceiver(receiver, filter);
  }

  private void findPaired() {
    BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
    if (bluetoothAdapter == null) return;

    if (!bluetoothAdapter.isEnabled()) {
      startBluetoothEnableRequest(request_find_paired);
    } else {
      Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
      if (pairedDevices.size() > 0) {
        dataSource.addAll(pairedDevices);
        if (adapter != null) adapter.notifyDataSetChanged();
      } else {
        if (debug) Log.d(TAG, "paired devices is empty !");
      }
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    stopDiscovery();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterReceiver(receiver);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == RESULT_OK) {
      if (requestCode == request_action_discovering) {
        startDiscovery();
      }
    }
  }

  private void startDiscovery() {
    BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
    if (bluetoothAdapter == null) {
      return;
    }

    if (!bluetoothAdapter.isEnabled()) {
      startBluetoothEnableRequest(request_action_discovering);
      return;
    }

    if (bluetoothAdapter.startDiscovery()) {
      if (debug) Log.d(TAG, "start discovery success !");
      dataSource.removeAll();
      findPaired();
      adapter.notifyDataSetChanged();
      updateMenu(true);
    } else {
      if (debug) Log.d(TAG, "start discovery failed !");
    }
  }

  private void stopDiscovery() {
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
      if (bluetoothAdapter.cancelDiscovery()) {
        if (debug) Log.d(TAG, "stop discovery success !");
        updateMenu(false);
      } else {
        if (debug) Log.d(TAG, "stop discovery failed !");
      }
    } else {
      if (debug) Log.d(TAG, "stop bluetoothAdapter is null or not enable !");
    }
  }

  private BluetoothAdapter getBluetoothAdapter() {
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter == null) {
      Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_SHORT).show();
      return null;
    }

    return bluetoothAdapter;
  }

  private void startBluetoothEnableRequest(int requestCode) {
    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    startActivityForResult(enableBtIntent, requestCode);
  }

  private void setupToolbar() {
    toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.inflateMenu(R.menu.device_select);
    toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
          case R.id.start_discovering:
            startDiscovery();
            break;
          case R.id.stop_discovering:
            stopDiscovery();
            break;
          default:
            return false;
        }
        return true;
      }
    });
    updateMenu(false);
  }

  private void updateMenu(boolean isDiscovering) {
    BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
    boolean hasBluetooth = bluetoothAdapter != null;
    toolbar.getMenu().findItem(R.id.start_discovering).setVisible(hasBluetooth && !isDiscovering);
    toolbar.getMenu().findItem(R.id.stop_discovering).setVisible(hasBluetooth && isDiscovering);
  }

  private void setupRv() {
    RecyclerView recyclerView = (RecyclerView) findViewById(R.id.rv);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));

    BSimpleItemDelegate<BluetoothDevice> itemDelegate = new BSimpleItemDelegate<BluetoothDevice>() {
      @Override
      protected View onCreateView(LayoutInflater inflater, ViewGroup parent) {
        return inflater.inflate(R.layout.item_device, parent, false);
      }

      @Override
      protected void onBind(BSimpleViewHolder holder, BluetoothDevice device) {
        holder.getTextView(R.id.name).setText(device.getName());
        holder.getTextView(R.id.mac).setText(device.getAddress());
        holder.getTextView(R.id.state)
            .setText(device.getBondState() == BluetoothDevice.BOND_BONDED ? "已配对" : "");
      }
    };
    itemDelegate.setOnItemClickListener(new OnItemClickListener<BluetoothDevice>() {
      @Override
      public void onItemClick(View view, int position, final BluetoothDevice device) {
        switch (device.getBondState()) {
          case BluetoothDevice.BOND_BONDED:
            Intent data = new Intent();
            data.putExtra(RESULT_BLUETOOTH_DEVICE, device);
            setResult(RESULT_OK, data);
            finish();
            break;
          case BluetoothDevice.BOND_NONE:
            showPairPopView(view, device);
            break;
        }
      }
    });
    adapter = BAdapter.builder().itemDelegate(itemDelegate).dataSource(dataSource).build();
    recyclerView.setAdapter(adapter);
  }

  private void showPairPopView(final View view, final BluetoothDevice device) {
    PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
    popupMenu.inflate(R.menu.device_click);
    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
          case R.id.device_pair:
            if (device.getBondState() == BluetoothDevice.BOND_NONE) {
              if (Build.VERSION.SDK_INT >= 19) {
                device.createBond();
              } else {
                try {
                  Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
                  createBondMethod.invoke(device);
                  if (debug) Log.d(TAG, "BOND_NONE createBond in < 19");
                } catch (Exception e) {
                  e.printStackTrace();
                  //do nothing
                }
              }
            }
            break;
          default:
            return false;
        }
        return true;
      }
    });
    popupMenu.show();
  }
}
