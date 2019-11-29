package com.example.vision03;

import android.os.Message;
import android.util.Log;

import java.io.DataInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 외부서버에서 전달하는 메세지를 쓰레드를 통해 받아내는 클래스, RecvThread
 */

public class RecvThread extends Thread {

    private SerialConnector mSerialConn = null;

    public static final int HEADER_MESSAGE_2 = 0x33333333;
    String move3;
    int motorstack = 0;

    private DataInputStream mDataInputStream;

    public RecvThread(InputStream is) {
        mDataInputStream = new DataInputStream(is);
    }

    @Override
    public void run() {
        int header, length;
        mSerialConn = new SerialConnector();

        try {
            while (true) {
                // (1) 헤더를 읽는다.
                header = mDataInputStream.readInt();
                length = mDataInputStream.readInt();
                switch (header) {
                    case HEADER_MESSAGE_2:  //writeUTF로 보내고 readUTF로 받는다
                        String message = mDataInputStream.readUTF();

                        if (message.equals("capture")) {
                            Message msg = Message.obtain();
                            msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                            msg.obj = message;
                            ServerThread.mMainHandler.sendMessage(msg);
                        }

                        Log.d("autoOn11111111","autoOn : " + message);
                        if (message.equals("autoOn")) {
                            Message msg = Message.obtain();
                            msg.what = MainActivity.CMD_AUTO_ON;
                            msg.obj = message;
                            ServerThread.mMainHandler.sendMessage(msg);
                        } else if (message.equals("autoOff")) {
                            Message msg = Message.obtain();
                            msg.what = MainActivity.CMD_AUTO_OFF;
                            msg.obj = message;
                            ServerThread.mMainHandler.sendMessage(msg);
                        }

                        //Log.d("[TaeHyeong]", message);
                        Message msg2 = Message.obtain();

                        msg2.what = MainActivity.CMD_BUTTON_ACTIVE;
                        msg2.obj = message;
                        ServerThread.mMainHandler.sendMessage(msg2);

                        Date today = new Date();
                        SimpleDateFormat date = new SimpleDateFormat("yy/MM/dd");
                        SimpleDateFormat time = new SimpleDateFormat("hh:mm:ss a");
                        ServerThread.doPrintln("[받은 메시지] " + message + "\n" + date.format(today) + " " + time.format(today));
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}