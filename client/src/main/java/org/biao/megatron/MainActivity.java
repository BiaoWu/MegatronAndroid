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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";
  private static final boolean debug = BuildConfig.DEBUG && true;

  private static final int request_action_discovering = 52066;
  private static final int request_find_paired = 52660;

  private BAdapter adapter;
  private BDataSource<BluetoothDevice> dataSource = new BDataSource<>();

  private boolean isDiscovering = false;

  private BroadcastReceiver receiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (debug) Log.d(TAG, "action -> " + action);
      if (BluetoothDevice.ACTION_FOUND.equals(action)) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        dataSource.add(device);
        adapter.notifyItemInserted(dataSource.size() - 1);
      } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
        int bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        int i = dataSource.getDataList().indexOf(device);
        if (debug) Log.d(TAG, "bond -> " + bond + ", device -> " + device + ", index -> " + i);
        switch (bond) {
          case BluetoothDevice.BOND_NONE:
          case BluetoothDevice.BOND_BONDED:
            if (i < 0) {
              dataSource.add(device);
              adapter.notifyItemInserted(dataSource.size() - 1);
            } else {
              adapter.notifyItemChanged(i);
            }
            break;
        }
      }
    }
  };

  private Toolbar toolbar;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

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
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
      if (debug) Log.d(TAG, "start bluetoothAdapter is null or not enable !");
      return;
    }

    isDiscovering = true;
    if (bluetoothAdapter.startDiscovery()) {
      dataSource.removeAll();
      findPaired();
      adapter.notifyDataSetChanged();
      if (debug) Log.d(TAG, "start discovery success !");
    } else {
      if (debug) Log.d(TAG, "start discovery failed !");
    }
  }

  private void stopDiscovery() {
    isDiscovering = false;

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
      if (bluetoothAdapter.cancelDiscovery()) {
        if (debug) Log.d(TAG, "stop discovery success !");
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
    toolbar.inflateMenu(R.menu.main);
    toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
          case R.id.action_discovering:
            BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
            if (bluetoothAdapter == null) {
              break;
            }

            if (isDiscovering) {
              if (bluetoothAdapter.isEnabled()) {
                stopDiscovery();
              }
            } else {
              if (bluetoothAdapter.isEnabled()) {
                startDiscovery();
              } else {
                startBluetoothEnableRequest(request_action_discovering);
              }
            }

            updateMenu();
            break;
          default:
            return false;
        }
        return true;
      }
    });
  }

  private void updateMenu() {
    MenuItem item = toolbar.getMenu().findItem(R.id.action_discovering);
    if (item != null) {
      item.setTitle(isDiscovering ? "停止扫描" : "重新扫描");
    }
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
        int bondState = device.getBondState();
        switch (bondState) {
          case BluetoothDevice.BOND_BONDED:
            // TODO: 2017/1/18 navigate to control
            break;
          case BluetoothDevice.BOND_BONDING:
            //do nothing
            if (debug) Log.d(TAG, "BOND_BONDING is here");
            break;
          case BluetoothDevice.BOND_NONE:
            if (Build.VERSION.SDK_INT >= 19) {
              device.createBond();
            } else {
              try {
                Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
                createBondMethod.invoke(device);
                if (debug) Log.d(TAG, "BOND_NONE createBond in < 19");
              } catch (NoSuchMethodException e) {
                e.printStackTrace();
              } catch (InvocationTargetException e) {
                e.printStackTrace();
              } catch (IllegalAccessException e) {
                e.printStackTrace();
              }
            }
            break;
        }
      }
    });
    adapter = BAdapter.builder().itemDelegate(itemDelegate).dataSource(dataSource).build();
    recyclerView.setAdapter(adapter);
  }
}
