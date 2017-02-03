package com.biao.megatron.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
  private static final String uuid = "fea73537-cb7b-4917-9993-c0fbc79d6016";

  private ExecutorService service;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    service = Executors.newCachedThreadPool();

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    service.submit(new ServerTask(bluetoothAdapter, service));
  }

  private static class ServerTask implements Runnable {
    private BluetoothAdapter bluetoothAdapter;
    private ExecutorService service;

    public ServerTask(BluetoothAdapter bluetoothAdapter, ExecutorService service) {
      this.bluetoothAdapter = bluetoothAdapter;
      this.service = service;
    }

    @Override
    public void run() {
      while (true) {
        try {
          BluetoothServerSocket bluetoothServerSocket =
              bluetoothAdapter.listenUsingRfcommWithServiceRecord("", UUID.fromString(uuid));
          BluetoothSocket socket = bluetoothServerSocket.accept();
          service.submit(new ClientTask(socket));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private static class ClientTask implements Runnable {
    private BluetoothSocket socket;

    public ClientTask(BluetoothSocket socket) {
      this.socket = socket;
    }

    @Override
    public void run() {
      try {
        InputStream in = socket.getInputStream();

        byte[] bytes = new byte[1024];
        while (!Thread.currentThread().isInterrupted()) {
          int len = in.read(bytes);
          Log.d("client", new String(bytes, 0, len));
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
