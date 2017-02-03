package org.biao.megatron;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;
import com.biao.joystick.JoystickView;
import java.io.IOException;

/**
 * TODO 增加油门
 */
public class ControlActivity extends AppCompatActivity {
  private static final String TAG = ControlActivity.class.getSimpleName();
  private static final boolean debug = BuildConfig.DEBUG;

  private static final String uuid = "fea73537-cb7b-4917-9993-c0fbc79d6016";

  private static final int request_device_select = 52066;

  private BluetoothDevice device;
  private Dispatcher dispatcher;

  private Toolbar toolbar;
  private TextView device_info;
  private JoystickView joystickView;

  private boolean showGuideLine = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    dispatcher = new Dispatcher(new Dispatcher.ConnectChangeListener() {
      @Override
      public void onChange(final boolean connected) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            setInfoWithDevice(connected ? "已连接" : "已断开连接");
          }
        });
      }
    });

    setContentView(R.layout.activity_control);
    setupToolbar();
    setupView();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    dispatcher.destroy();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == RESULT_OK) {
      if (requestCode == request_device_select) {
        BluetoothDevice newDevice =
            data.getParcelableExtra(DeviceSelectActivity.RESULT_BLUETOOTH_DEVICE);

        if (newDevice != device) {
          if (device != null) {
            try {
              dispatcher.disconnect();
            } catch (IOException e) {
              setInfoWithDevice(e.getMessage());
            }
          }

          device = newDevice;
          setInfoWithDevice("设备已选择");
          updateMenu();
        }
      }
    }
  }

  private void setInfoWithDevice(String state) {
    device_info.setText("device name : "
        + device.getName()
        + "\n"
        + "device address : "
        + device.getAddress()
        + "\n"
        + "state : "
        + state);
  }

  private void setupView() {
    device_info = (TextView) findViewById(R.id.device_info);

    joystickView = (JoystickView) findViewById(R.id.joystickView);
    joystickView.setPanelColor(Color.LTGRAY);
    joystickView.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
      @Override
      public void onChanged(int angle, double power) {
        dispatcher.dispatch(angle, power);
      }
    });
  }

  private void setupToolbar() {
    toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.inflateMenu(R.menu.control);
    toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
          case R.id.device_select:
            selectDevice();
            break;
          case R.id.device_connect:
            if (device != null) {
              try {
                dispatcher.connect(device, uuid);
              } catch (IOException e) {
                setInfoWithDevice(e.getMessage());
              }
            }
            break;
          case R.id.device_disconnect:
            if (device != null) {
              try {
                dispatcher.disconnect();
              } catch (IOException e) {
                setInfoWithDevice(e.getMessage());
              }
            }
            break;
          case R.id.toggle_control_line:
            joystickView.setDegrees(showGuideLine ? new int[0] : dispatcher.degrees());
            joystickView.setGuideLineColor(showGuideLine ? Color.TRANSPARENT : Color.GREEN);
            joystickView.setInnerAreaStrokeColor(showGuideLine ? Color.TRANSPARENT : Color.GREEN);
            showGuideLine = !showGuideLine;
            break;
          default:
            return false;
        }
        return true;
      }
    });
    updateMenu();
  }

  private void selectDevice() {
    Intent intent = new Intent(toolbar.getContext(), DeviceSelectActivity.class);
    startActivityForResult(intent, request_device_select);
  }

  private void updateMenu() {
    boolean hasDevice = device != null;
    boolean isConnected = dispatcher.isConnected();
    toolbar.getMenu().findItem(R.id.device_connect).setVisible(hasDevice && !isConnected);
    toolbar.getMenu().findItem(R.id.device_disconnect).setVisible(hasDevice && isConnected);
  }
}
