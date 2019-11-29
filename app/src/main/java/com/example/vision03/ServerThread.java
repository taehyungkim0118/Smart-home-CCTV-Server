package com.example.vision03;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.text.format.Formatter;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

@SuppressWarnings("deprecation")
/**
 * 서버 쓰레드 실행 클래스, RecvThread & SendThread Start
 */

public class ServerThread extends Thread {

    private Context mContext;
    public static Handler mMainHandler;

    public ServerThread(Context context, Handler mainHandler) {
        mContext = context;
        mMainHandler = mainHandler;
    }

    @Override
    public void run() {
        ServerSocket servSock = null;
        try {
            // 서버 소켓을 초기화한다.
            servSock = new ServerSocket(9000);
            // 서버의 IP 주소와 포트 번호를 출력한다.
            doPrintln(">> 서버 시작! " + getDeviceIp() + "/" + servSock.getLocalPort());

            Message msg = Message.obtain();
            msg.what = MainActivity.CMD_SET_TEXT;
            msg.obj = getDeviceIp();
            mMainHandler.sendMessage(msg);


            while (true) {
                // 클라이언트 접속을 기다린다.
                Socket sock = servSock.accept();
                // 접속한 클라이언트 정보를 출력한다.
                String ip = sock.getInetAddress().getHostAddress();
                int port = sock.getPort();
                doPrintln(">> 클라이언트 접속: " + ip + "/" + port);
                // 별도의 스레드로 클라이언트와 통신한다.
                try {
                    SendThread sendThread = new SendThread(sock.getOutputStream());
                    RecvThread recvThread = new RecvThread(sock.getInputStream());
                    recvThread.start();
                    sendThread.start();
                    recvThread.join();
                    sendThread.join();
                } catch (Exception e) {
                    doPrintln(e.getMessage());
                }
                sock.close();
                doPrintln(">> 클라이언트 종료: " + ip + "/" + port);
            } // end of while-loop
        } catch (Exception e) {
            doPrintln(e.getMessage());
        } finally {
            try {
                if (servSock != null) {
                    servSock.close();
                }
                doPrintln(">> 서버 종료!");
            } catch (IOException e) {
                doPrintln(e.getMessage());
            }
        }
    } // end of run()

    public static void doPrintln(String str) {  //메인 핸들러에 메시지 보내서 TextView 문자띄우는 메서드
        Message msg = Message.obtain();
        msg.what = MainActivity.CMD_APPEND_TEXT;
        msg.obj = str + "\n";
        mMainHandler.sendMessage(msg);
    }

    private String getDeviceIp() {
        String ipaddr = getWifiIp();
        if (ipaddr == null)
            ipaddr = getMobileIp();
        if (ipaddr == null)
            ipaddr = "127.0.0.1";
        return ipaddr;
    }

    private String getWifiIp() {
        WifiManager wifiMgr = (WifiManager) mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiMgr != null && wifiMgr.isWifiEnabled()) {
            int ip = wifiMgr.getConnectionInfo().getIpAddress();
            return Formatter.formatIpAddress(ip);
        }
        return null;
    }

    private String getMobileIp() {
        try {
            for (Enumeration<NetworkInterface> e1 = NetworkInterface.getNetworkInterfaces(); e1.hasMoreElements(); ) {
                NetworkInterface networkInterface = e1.nextElement();
                for (Enumeration<InetAddress> e2 = networkInterface.getInetAddresses(); e2.hasMoreElements(); ) {
                    InetAddress inetAddress = e2.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String host = inetAddress.getHostAddress();
                        if (!TextUtils.isEmpty(host)) {
                            return host;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
