package com.example.vision03;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 본 서버에서 메세지를 보내주는 클래스, SendThread
 */
public class SendThread extends Thread {

    public static final int CMD_SEND_BITMAP = 1;
    public static final int CMD_SEND_MESSAGE = 2;

    private static final int HEADER_BITMAP = 0x11111111;
    private static final int HEADER_MESSAGE = 0x22222222;

    private DataOutputStream mDataOutputStream;
    public static Handler mHandler;

    public SendThread(OutputStream os) {
        mDataOutputStream = new DataOutputStream(os);
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                byte[] byteArray;
                try {
                    switch (msg.what) {
                        case CMD_SEND_BITMAP: // 비트맵 전송
                            Bitmap bitmap = (Bitmap) msg.obj;
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream);
                            byteArray = stream.toByteArray();
                            // 헤더 + 길이 + 데이터 순으로 보낸다.
                            mDataOutputStream.writeInt(HEADER_BITMAP);
                            mDataOutputStream.writeInt(byteArray.length);
                            mDataOutputStream.write(byteArray);
                            mDataOutputStream.flush();
                            break;
                        case CMD_SEND_MESSAGE: // 데이터 송신 메시지
                            try {
                                String s = (String) msg.obj;
//                                if(s.contains("Face")) {
//                                    msg.what = MainActivity.FACE_DETECTED;
//                                    ServerThread.mMainHandler.sendMessage(msg);
//                                } else if(s.contains("Cat")) {
//                                    msg.what = MainActivity.CAT_DETECTED;
//                                    ServerThread.mMainHandler.sendMessage(msg);
//                                } else if(s.contains("Dog")) {
//                                    msg.what = MainActivity.DOG_DETECTED;
//                                    ServerThread.mMainHandler.sendMessage(msg);
//                                }
                                mDataOutputStream.writeInt(HEADER_MESSAGE);
                                mDataOutputStream.writeInt(s.length());
                                mDataOutputStream.writeUTF(s);
                                //mDataOutputStream.write(s.getBytes()); 길이 없이 전송한 경우 사용
                                mDataOutputStream.flush();

                                Date today = new Date();
                                SimpleDateFormat date = new SimpleDateFormat("yy/MM/dd");
                                SimpleDateFormat time = new SimpleDateFormat("hh:mm:ss a");
                                ServerThread.doPrintln("[보낸 메시지] " + s + "\n" + date.format(today) + " " + time.format(today));
                            } catch (IOException e) {
                                ServerThread.doPrintln(e.getMessage());
                            }
                            break;
                    }
                } catch (Exception e) {
                    getLooper().quit();
                }
            }
        };
        Looper.loop();
    }
}