package org.biao.megatron;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.text.TextUtils;
import android.util.Log;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author biaowu.
 */
class Dispatcher {
  private static final String TAG = "Dispatcher";

  private final int[] degrees = { 0, 60, 120, 180, 240, 300 };

  interface ConnectChangeListener {
    void onChange(boolean connected);
  }

  private BluetoothDevice device;
  private BluetoothSocket socket;
  private ConnectChangeListener connectChangeListener;

  private ExecutorService service;
  private String currentCommand = Command.empty;

  private PublishSubject<String> motion = PublishSubject.create();
  private Disposable disposable;

  Dispatcher(ConnectChangeListener connectChangeListener) {
    this.connectChangeListener = connectChangeListener;
    service = Executors.newFixedThreadPool(2);

    disposable = motion.sample(50, TimeUnit.MILLISECONDS).subscribe(new Consumer<String>() {
      @Override
      public void accept(String command) throws Exception {
        if (TextUtils.isEmpty(command)) return;

        if (currentCommand.equals(command)) {
          return;
        }

        try {
          send(command.getBytes());
          currentCommand = command;
        } catch (IOException e) {
          //do nothing
        }
      }
    });
  }

  void destroy() {
    service.shutdown();
    if (!disposable.isDisposed()) {
      disposable.dispose();
    }
  }

  void connect(BluetoothDevice device, String uuid) throws IOException {
    checkDevice(device);

    if (this.device != device) {
      if (this.device != null) {
        disconnect();
      }

      this.device = device;
      try {
        socket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid));
        socket.connect();
        connectChangeListener.onChange(true);
        service.submit(new ReadTask(socket, connectChangeListener));
      } catch (IOException e) {
        throw new IOException("连接失败");
      }
    }
  }

  void disconnect() throws IOException {
    if (socket != null && socket.isConnected()) {
      try {
        socket.close();
      } catch (IOException e) {
        throw new IOException("断开连接失败");
      }
    }
  }

  boolean isConnected() {
    return device != null && socket != null && socket.isConnected();
  }

  void dispatch(int angle, double power) {
    motion.onNext(transform(angle, power));
  }

  int[] degrees() {
    return degrees;
  }

  private void send(byte[] bytes) throws IOException {
    checkDevice(device);

    if (isConnected()) {
      OutputStream out = socket.getOutputStream();
      out.write(bytes);
      out.flush();
    }
  }

  private void checkDevice(BluetoothDevice device) throws IOException {
    if (device == null) {
      throw new IOException("没有选择设备");
    } else if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
      throw new IOException("设备还未配对");
    }
  }

  private String transform(int angle, double power) {
    if (power <= 0 || power > 1) return Command.stop;

    if (angle <= degrees[0]) {
      return Command.turn_right_backward;
    } else if (angle <= degrees[1]) {
      return Command.turn_right_forward;
    } else if (angle <= degrees[2]) {
      return Command.forward;
    } else if (angle <= degrees[3]) {
      return Command.turn_left_forward;
    } else if (angle <= degrees[4]) {
      return Command.turn_left_backward;
    } else if (angle <= degrees[5]) {
      return Command.backward;
    } else {
      return Command.turn_right_backward;
    }
  }

  private static class ReadTask implements Runnable {

    private BluetoothSocket socket;
    private ConnectChangeListener connectChangeListener;

    ReadTask(BluetoothSocket socket, ConnectChangeListener connectChangeListener) {
      this.socket = socket;
      this.connectChangeListener = connectChangeListener;
    }

    @Override
    public void run() {
      if (socket == null) return;

      try {
        InputStream inputStream = socket.getInputStream();

        byte[] buf = new byte[1024];
        while (!Thread.currentThread().isInterrupted()) {
          int len = inputStream.read(buf);
          Log.e(TAG, new String(buf, 0, len));
        }
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        try {
          socket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
        Log.e(TAG, "read end!!!");
        connectChangeListener.onChange(false);
      }
    }
  }
}
