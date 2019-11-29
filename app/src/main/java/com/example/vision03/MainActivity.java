package com.example.vision03;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnClickListener {

    // USB control에 사용되는 객체.
    private Context mContext = null;
    private ActivityHandler mHandler2 = null;

    private SerialListener mListener = null;
    private SerialConnector mSerialConn = null;

    //*****************************************

    public static final int CMD_APPEND_TEXT = 0;
    public static final int CMD_BUTTON_ACTIVE = 1;
    public static final int CMD_SHOW_MESSAGE = 3;
    public static final int CMD_APPEND_IMG = 4;

    public static final int CMD_DETECT_ON = 66;
    public static final int CMD_DETECT_OFF = 67;
    public static final int CMD_AUTO_ON = 68;
    public static final int CMD_AUTO_OFF = 69;
    public static final int CMD_SET_TEXT = 70;

    boolean autoDetectionOnOff = false;
    private TextView textautoDetectionOnOff;
    private TextView mTextStatus;
    private ServerThread mServerThread;
    private static final int MAX_LABEL_RESULTS = 10;
    private static final int MAX_DIMENSION = 1200;

    private static final String CLOUD_VISION_API_KEY = BuildConfig.API_KEY;
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";

    int facestack = 0;
    int motorstack = 0;

    int motorUp = 0;
    int motorDown = 0;

    private static final String TAG = "opencv";
    private CameraBridgeViewBase mOpenCvCameraView;
    private ImageView mimageView;
    private Mat matInput;
    private Mat matResult;

    private TextView mdetectedOrNot;
    private TextView mmotionDetect;
    private TextView mIpNumber;

    public native long loadCascade(String cascadeFileName);

    public native int detect(long cascadeClassifier_face,
                             long cascadeClassifier_eye, long matAddrInput, long matAddrResult);

    public native double detect4(long cascadeClassifier_face, long cascadeClassifier_eye, long matAddrInput, long matAddrResult);

    public native double detect5(long cascadeClassifier_face, long cascadeClassifier_eye, long matAddrInput, long matAddrResult);

    public native double faceWidth(long cascadeClassifier_face, long cascadeClassifier_eye, long matAddrInput, long matAddrResult);

    public native double faceHeight(long cascadeClassifier_face, long cascadeClassifier_eye, long matAddrInput, long matAddrResult);

    public long cascadeClassifier_face = 0;
    public long cascadeClassifier_eye = 0;

    private final Semaphore writeLock = new Semaphore(2);

    public void getWriteLock() throws InterruptedException {
        writeLock.acquire();
    }

    public void releaseWriteLock() {
        writeLock.release();
    }

    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    private void copyFile(String filename) {
        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator + filename;

        AssetManager assetManager = this.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d(TAG, "copyFile :: 다음 경로로 파일복사 " + pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d(TAG, "copyFile :: 파일 복사 중 예외 발생 " + e.toString());
        }
    }

    private void read_cascade_file() {
        copyFile("haarcascade_frontalface_alt.xml");
        copyFile("haarcascade_eye_tree_eyeglasses.xml");

        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_face = loadCascade("haarcascade_frontalface_alt.xml");
        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_eye = loadCascade("haarcascade_eye_tree_eyeglasses.xml");
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {  //Activity 생성시 자동으로 호출(최초 호출되는 특징으로 재정의하여 각종 초기화 코드를 넣는 경우가 많음)
        super.onCreate(savedInstanceState);  //슈퍼클래스의 onCreate() 메서드 호출 (기본 초기화)

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,     //상태바 없애는 코드
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);  //Activity 화면 초기화
        mTextStatus = (TextView) findViewById(R.id.textStatus);
        mTextStatus.setVisibility(View.INVISIBLE);
        mimageView = (ImageView) findViewById(R.id.imageView);

        mdetectedOrNot = (TextView) findViewById(R.id.detectedOrNot);
        mIpNumber = (TextView) findViewById(R.id.ipNumber);

        textautoDetectionOnOff = (TextView) findViewById(R.id.autoOnOff);


        if (mServerThread == null) { // 서버 시작
            mServerThread = new ServerThread(this, mMainHandler);
            mServerThread.start();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //퍼미션 상태 확인
            if (!hasPermissions(PERMISSIONS)) {

                //퍼미션 허가 안되어있다면 사용자에게 요청
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            } else read_cascade_file(); //추가
        } else read_cascade_file(); //추가

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(1); // front-camera(1),  back-camera(0)
        mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);

        // +
        // System
        mContext = getApplicationContext();

        // Initialize
        mListener = new SerialListener();
        mHandler2 = new ActivityHandler();

        // Initialize Serial connector and starts Serial monitoring thread.
        mSerialConn = new SerialConnector(mContext, mListener, mHandler2);
        mSerialConn.initialize();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() { //생애주기 마지막 단계
        super.onDestroy();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        mSerialConn.finalize();   //+
    }

    @Override
    public void onCameraViewStarted(int width, int height) {  //Override 해야하는 메서드

    }

    @Override
    public void onCameraViewStopped() {    //Override 해야하는 메서드

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) { // 매 프레임마다 동작

        try {
            getWriteLock();

            matInput = inputFrame.rgba();

            if (matResult == null)

                matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());
            Core.flip(matInput, matInput, 1);   //카메라화면 180도 뒤집기(전면, 후면 카메라 변경시 확인)

            int ret = detect(cascadeClassifier_face, cascadeClassifier_eye, matInput.getNativeObjAddr(),  //검출된 얼굴 개수 대입
                    matResult.getNativeObjAddr());

            double real_facesize_x = detect4(cascadeClassifier_face, cascadeClassifier_eye, matInput.getNativeObjAddr(),
                    matResult.getNativeObjAddr());

            double real_facesize_y = detect5(cascadeClassifier_face, cascadeClassifier_eye, matInput.getNativeObjAddr(),
                    matResult.getNativeObjAddr());

            // 얼굴이 2개 이상 검출될 경우 큰 사이즈의 값을 가져와 주는 좌표 값
            double facewidth = faceWidth(cascadeClassifier_face, cascadeClassifier_eye, matInput.getNativeObjAddr(),
                    matResult.getNativeObjAddr());

            double faceheight = faceHeight(cascadeClassifier_face, cascadeClassifier_eye, matInput.getNativeObjAddr(),
                    matResult.getNativeObjAddr());

            // 얼굴이 2개 이상 검출될 경우 큰 사이즈로 검출된 얼굴을 인식 및 자동 추적
            if (ret >= 2 && autoDetectionOnOff) {
                Log.d("taehyung", "face " + ret + " found");
                Log.d("taehyung", "X좌표" + facewidth);
                Log.d("taehyung", "Y좌표" + faceheight);

                facestack++;
                if (facestack % 100 == 50) {
                    if (SendThread.mHandler != null) {  //SendThread에 핸들러 존재시 실행
                        Message msg = Message.obtain();
                        msg.what = SendThread.CMD_SEND_MESSAGE;
                        msg.obj = "얼굴이 검출되었습니다";
                        SendThread.mHandler.sendMessage(msg);  //작성한 메시지를 SendThread로 핸들러를 이용해 보냄
                    }
                }

                if (faceheight >= 320) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                    msg.obj = "DOWN";
                    ServerThread.mMainHandler.sendMessage(msg);
                    Log.d("자동추적테스트", "아두이노 동작 확인 상");
                } else if (faceheight <= 160) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                    msg.obj = "UP";
                    ServerThread.mMainHandler.sendMessage(msg);
                    Log.d("자동추적테스트", "아두이노 동작 확인 하");
                }
                if (facewidth >= 430) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                    msg.obj = "RIGHT";
                    ServerThread.mMainHandler.sendMessage(msg);
                    Log.d("자동추적테스트", "아두이노 동작 확인 좌");
                } else if (facewidth <= 210) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                    msg.obj = "LEFT";
                    ServerThread.mMainHandler.sendMessage(msg);
                    Log.d("자동추적테스트", "아두이노 동작 확인 우");
                }

            } else if (ret == 1 && autoDetectionOnOff) {   //얼굴 1개
                Log.d("TaeHyeong1", "face " + ret + " found");
                Log.d("Test2", "X좌표" + real_facesize_x);
                Log.d("Test2", "Y좌표" + real_facesize_y);

                facestack++;
                if (facestack % 100 == 50) {
                    if (SendThread.mHandler != null) {  //SendThread에 핸들러 존재시 실행
                        Message msg = Message.obtain();
                        msg.what = SendThread.CMD_SEND_MESSAGE;
                        msg.obj = "얼굴이 검출되었습니다";
                        SendThread.mHandler.sendMessage(msg);  //작성한 메시지를 SendThread로 핸들러를 이용해 보냄
                    }
//                    capture();
                }

                if (real_facesize_y >= 320) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                    msg.obj = "DOWN";
                    ServerThread.mMainHandler.sendMessage(msg);
                    Log.d("자동추적테스트", "아두이노 동작 확인 상");
                } else if (real_facesize_y <= 160) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                    msg.obj = "UP";
                    ServerThread.mMainHandler.sendMessage(msg);
                    Log.d("자동추적테스트", "아두이노 동작 확인 하");
                }
                if (real_facesize_x >= 430) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                    msg.obj = "RIGHT";
                    ServerThread.mMainHandler.sendMessage(msg);
                    Log.d("자동추적테스트", "아두이노 동작 확인 좌");
                } else if (real_facesize_x <= 210) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.CMD_BUTTON_ACTIVE;
                    msg.obj = "LEFT";
                    ServerThread.mMainHandler.sendMessage(msg);
                    Log.d("자동추적테스트", "아두이노 동작 확인 우");
                }

            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        releaseWriteLock();

        Bitmap bitmap = Bitmap.createBitmap(matResult.cols(), matResult.rows(), Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(matResult, bitmap);       // Mat을 Bitmap으로 변환

        sendBitmapThroughNetwork(bitmap);            // 네트워크로 전송 <- 기존 VideoServer에 있던 함수  핸들러로 처리함

        sendBitmapToViewer(bitmap);                  // ImageView에 보여줌   핸들러로 처리함

        return matResult;
    }

    private void sendBitmapToViewer(Bitmap bitmap) {
        Message msg = Message.obtain();
        msg.what = CMD_APPEND_IMG;
        msg.obj = bitmap;
        mMainHandler.sendMessage(msg);   // mMainHandler에 bitmap을 전달!
    }

    //여기서부턴 퍼미션 관련 메소드
    static final int PERMISSIONS_REQUEST_CODE = 1000;
    //String[] PERMISSIONS  = {"android.permission.CAMERA"};
    String[] PERMISSIONS = {"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE"};


    private boolean hasPermissions(String[] permissions) { //Permission 관련 코드
        int result;

        //스트링 배열에 있는 퍼미션들의 허가 상태 여부 확인
        for (String perms : permissions) {

            result = ContextCompat.checkSelfPermission(this, perms);

            if (result == PackageManager.PERMISSION_DENIED) {
                //허가 안된 퍼미션 발견
                return false;
            }
        }
        //모든 퍼미션이 허가되었음
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,    //Permission 관련 코드
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {

            case PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraPermissionAccepted = grantResults[0]
                            == PackageManager.PERMISSION_GRANTED;

                    //if (!cameraPermissionAccepted)
                    //   showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
                    boolean writePermissionAccepted = grantResults[1]
                            == PackageManager.PERMISSION_GRANTED;

                    if (!cameraPermissionAccepted || !writePermissionAccepted) {
                        showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
                        return;
                    } else {
                        read_cascade_file();
                    }
                }
                break;
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {    //Permission 관련 코드

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }

    public void mOnClick(View v) {  //버튼 클릭시 이벤트
        switch (v.getId()) {
            case R.id.btnQuit:
                finish();
                break;
            case R.id.setVisible:
                ToggleButton tb = (ToggleButton) findViewById(R.id.setVisible);
                if (tb.isChecked()) {
                    mTextStatus.setVisibility(View.INVISIBLE);
                } else {
                    mTextStatus.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CMD_APPEND_TEXT: // 텍스트 출력
                    mTextStatus.append((String) msg.obj);
                    scroll();
                    break;
                case CMD_SHOW_MESSAGE: //메시지 출력
                    mTextStatus.append((String) msg.obj);
                    scroll();
                    break;
                case CMD_APPEND_IMG:  //imageView 에 bitmap 띄움
                    mimageView.setImageBitmap((Bitmap) msg.obj);
                    break;

                case CMD_AUTO_ON: // 얼굴 자동 인식 on/off
                    autoDetectionOnOff = true;
                    textautoDetectionOnOff.setText("ON");
                    break;

                case CMD_AUTO_OFF:
                    autoDetectionOnOff = false;
                    textautoDetectionOnOff.setText("OFF");
                    break;

                case CMD_SET_TEXT:
                    mIpNumber.setText((String) msg.obj);
                    break;

                case CMD_BUTTON_ACTIVE: //클라이언트에서 버튼입력으로 받은 데이터처리
                    String move3;
                    //음성 모터제어 명령어
                    if (msg.obj.equals("반대편 보여 줘")) {
                        motorstack++;
                        if (motorstack % 2 == 1) {
                            for (int i = 0; i <= 30; i++) {  // i = 14
                                move3 = "d"; // d = 우 14   c = 좌 18
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        } else if (motorstack % 2 == 0) {
                            for (int j = 0; j <= 27; j++) {  // i = 18
                                move3 = "c"; // d = 우 14   c = 좌 18
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        }
                    } else if (msg.obj.equals("반대편 보여줘")) {
                        motorstack++;
                        if (motorstack % 2 == 1) {
                            for (int i = 0; i <= 30; i++) {
                                move3 = "d"; // d = 우 14   c = 좌 18
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        } else if (motorstack % 2 == 0) {
                            for (int j = 0; j <= 27; j++) {
                                move3 = "c"; // d = 우 14   c = 좌 18
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        }
                    }
                    if (msg.obj.equals("왼쪽 보여 줘")) {
                        for (int i = 0; i < 10; i++) {
                            move3 = "c";
//                            mSerialConn.sendCommand(move3);
                            mSerialConn.sendCommand(move3);
                        }
                    }
                    if (msg.obj.equals("오른쪽 보여 줘")) {
                        for (int i = 0; i < 10; i++) {
                            move3 = "d";
//                            mSerialConn.sendCommand(move3);
                            mSerialConn.sendCommand(move3);
                        }
                    }
                    if (msg.obj.equals("위에 보여 줘")) {
                        motorUp++;
                        if (motorDown > 0) {
                            motorDown = 0;
                            for (int i = 0; i < 12; i++) {
                                move3 = "a";
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        } else {
                            for (int i = 0; i < 12; i++) {
                                move3 = "a";
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        }
                    } else if (msg.obj.equals("위쪽 보여 줘")) {
                        motorUp++;
                        if (motorDown > 0) {
                            motorDown = 0;
                            for (int i = 0; i < 12; i++) {
                                move3 = "a";
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        } else {
                            for (int i = 0; i < 12; i++) {
                                move3 = "a";
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        }
                    }
                    if (msg.obj.equals("아래 보여 줘")) {
                        motorDown++;
                        if (motorUp > 0) {
                            motorUp = 0;
                            for (int i = 0; i < 12; i++) {
                                move3 = "b";
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        } else {
                            for (int i = 0; i < 12; i++) {
                                move3 = "b";
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        }
                    } else if (msg.obj.equals("아래쪽 보여 줘")) {
                        motorDown++;
                        if (motorUp > 0) {
                            motorUp = 0;
                            for (int i = 0; i < 12; i++) {
                                move3 = "b";
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        } else {
                            for (int i = 0; i < 12; i++) {
                                move3 = "b";
//                                mSerialConn.sendCommand(move3);
                                mSerialConn.sendCommand(move3);
                            }
                        }
                    }

                    if (msg.obj.equals("UP")) {
                        move3 = "a";
//                        mSerialConn.sendCommand(move3);
                        mSerialConn.sendCommand(move3);
                        Log.d("TaeHyeong", "아두이노 동작 확인1");
                    } else if (msg.obj.equals("DOWN")) {
                        move3 = "b";
//                        mSerialConn.sendCommand(move3);
                        mSerialConn.sendCommand(move3);
                        Log.d("TaeHyeong", "아두이노 동작 확인2");
                    } else if (msg.obj.equals("LEFT")) {
                        move3 = "c";
//                        mSerialConn.sendCommand(move3);
                        mSerialConn.sendCommand(move3);
                        Log.d("TaeHyeong", "아두이노 동작 확인3");
                    } else if (msg.obj.equals("RIGHT")) {
                        move3 = "d";
//                        mSerialConn.sendCommand(move3);
                        mSerialConn.sendCommand(move3);
                        Log.d("TaeHyeong", "아두이노 동작 확인4");
                    } else if (msg.obj.equals("캡쳐") || msg.obj.equals("사진") || msg.obj.equals("사진 찍어") || msg.obj.equals("촬영")) {
                        capture();
                        Log.d("TaeHyeong", "capture 동작 확인");
                    }
                    Log.d("TaeHyeong", "아두이노 동작 확인");
                    break;
            }
        }
    };

    private void sendBitmapThroughNetwork(Bitmap bitmap) {  //핸들러로 비트맵이미지를 SendThread로 보냄
        if (SendThread.mHandler == null) return;  //SendThread에 핸들러 없는경우 return
        if (SendThread.mHandler.hasMessages(SendThread.CMD_SEND_BITMAP)) {  //SendThread의 핸들러에 이미 데이터가 존재하는 경우 제거
            SendThread.mHandler.removeMessages(SendThread.CMD_SEND_BITMAP);
        }
        Message msg = Message.obtain();
        msg.what = SendThread.CMD_SEND_BITMAP;
        msg.obj = bitmap;
        SendThread.mHandler.sendMessage(msg);
    }

    @Override
    public void onClick(View v) {

    }

    // USB Control에 해당하는 코드들.
    public class SerialListener {
        public void onReceive(int msg, int arg0, int arg1, String arg2, Object arg3) {
            switch (msg) {
                case Constants.MSG_DEVICD_INFO:
                    mTextStatus.append(arg2);
                    break;
                case Constants.MSG_DEVICE_COUNT:
                    mTextStatus.append(Integer.toString(arg0) + " device(s) found \n");
                    break;
                case Constants.MSG_READ_DATA_COUNT:
                    mTextStatus.append(Integer.toString(arg0) + " buffer received \n");
                    break;
                case Constants.MSG_READ_DATA:
                    if (arg3 != null) {
                        mTextStatus.append((String) arg3);
                        mTextStatus.append("\n");
                    }
                    break;
                case Constants.MSG_SERIAL_ERROR:
//                    mTextStatus.append(arg2);
                    break;
                case Constants.MSG_FATAL_ERROR_FINISH_APP:
                    finish();
                    break;
            }
        }
    }

    // usb 통신으로 들어오는 메세지를 다루는 핸들러
    public class ActivityHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MSG_DEVICD_INFO:
//                    mTextStatus.append((String) msg.obj);
                    break;
                case Constants.MSG_DEVICE_COUNT:
//                    mTextStatus.append(Integer.toString(msg.arg1) + " device(s) found \n");
                    break;
                case Constants.MSG_READ_DATA_COUNT:
//                    mTextStatus.append(((String) msg.obj));
                    if (SendThread.mHandler != null) {
//                        mmotionDetect.setText("움직임이 감지되었습니다");
                        Message msg1 = Message.obtain();
                        msg1.what = SendThread.CMD_SEND_MESSAGE;
                        msg1.obj = "detect";
                        SendThread.mHandler.sendMessage(msg1);
                        capture();
                        scroll();
                    }
                    break;
                /*case Constants.MSG_READ_DATA:
                    if(msg.obj != null) {
                        mTextStatus.append((String)msg.obj);
                        mTextStatus.append("\n");
                    }
                    break;*/
                case Constants.MSG_SERIAL_ERROR:
//                    mTextStatus.append((String) msg.obj);
                    break;
            }
        }
    }

    // 화면 캡처 메서드
    public void capture() {
        try {
            getWriteLock();

            File path = new File(Environment.getExternalStorageDirectory() + "/Images/");
            path.mkdirs();
            File file = new File(path, "image.png");

            String filename = file.toString();

            Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGB, 4);
            boolean ret = Imgcodecs.imwrite(filename, matResult);
            if (ret) Log.d(TAG, "SUCESS");
            else Log.d(TAG, "FAIL");

            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(file));
            sendBroadcast(mediaScanIntent);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    mdetectedOrNot.setText("분석 중...");
                    uploadImage(Uri.fromFile(file));
                }
            }).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        releaseWriteLock();
    }

    // 텍스트 창을 자동으로 내려주는 메서드
    public void scroll() {
        final ScrollView scrollView = ((ScrollView) findViewById(R.id.ScrollView));
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }


    // Vision API 분석 메서드 Start
    public void uploadImage(Uri uri) {
        Log.d("uploadImage", "uploadImage");
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap =
                        scaleBitmapDown(
                                MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                                MAX_DIMENSION);
                Log.d("callCloudVision", "callCloudVision");
                callCloudVision(bitmap);
//                mMainImage.setImageBitmap(bitmap);

            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, "Something is wrong with that image. Pick a different on...", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, "Something is wrong with that image. Pick a different on...", Toast.LENGTH_LONG).show();
        }
    }

    private void callCloudVision(Bitmap bitmap) {
        // Switch text to loading
        Log.d("callCloudVision", "setText");
//        mImageDetails.setText("Uploading image. Please wait.");
        // Do the real work in an async task, because we need to use the network anyway
        try {
            AsyncTask<Object, Void, String> labelDetectionTask = new LableDetectionTask(this, prepareAnnotationRequest(bitmap));
            labelDetectionTask.execute();
            Log.d("callCloudVision", "execute");
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " +
                    e.getMessage());
        }
    }

    private static class LableDetectionTask extends AsyncTask<Object, Void, String> {
        private final WeakReference<MainActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;

        LableDetectionTask(MainActivity activity, Vision.Images.Annotate annotate) {
            mActivityWeakReference = new WeakReference<>(activity);
            mRequest = annotate;
        }

        @Override
        protected String doInBackground(Object... params) {
            try {
                Log.d(TAG, "created Cloud Vision request object, sending request11");
                BatchAnnotateImagesResponse response = mRequest.execute();
                Log.d(TAG, "response requested");
                return convertResponseToString(response);

            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());
            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " +
                        e.getMessage());
            }
            return "Cloud Vision API request failed. Check logs for details.";
        }

        protected void onPostExecute(String result) {
            MainActivity activity = mActivityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                TextView detectedName = activity.findViewById(R.id.objectName);
                TextView detected = activity.findViewById(R.id.detectedOrNot);
//                TextView motion = activity.findViewById(R.id.motionDetect);
//                motion.setText("움직임 감지 대기중...");
                detected.setText("분석 결과");
                if (result.contains("사람")) {
                    detectedName.setText("사람");
                    if (SendThread.mHandler != null) {
                        Message msg1 = Message.obtain();
                        msg1.what = SendThread.CMD_SEND_MESSAGE;
                        msg1.obj = "사람 감지";
                        SendThread.mHandler.sendMessage(msg1);
                    }

                } else if (result.contains("고양이")) {
                    detectedName.setText("고양이");
                    if (SendThread.mHandler != null) {
                        Message msg1 = Message.obtain();
                        msg1.what = SendThread.CMD_SEND_MESSAGE;
                        msg1.obj = "고양이 감지";
                        SendThread.mHandler.sendMessage(msg1);
                    }
                } else if (result.contains("강아지")) {
                    detectedName.setText("강아지");
                    if (SendThread.mHandler != null) {
                        Message msg1 = Message.obtain();
                        msg1.what = SendThread.CMD_SEND_MESSAGE;
                        msg1.obj = "강아지 감지";
                        SendThread.mHandler.sendMessage(msg1);
                    }
                }

//                else
//                    detectedName.setText(result);
            }
        }
    }

    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    private static String convertResponseToString(BatchAnnotateImagesResponse response) {
        Log.d("convertResponse", "convertResponseToString");
        StringBuilder message = new StringBuilder();

        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        Log.d("converResponse", "conver2222222");

        List labeList = new ArrayList(); // 분석된 결과를 저장하는 리스트

        if (labels != null) {
            for (EntityAnnotation label : labels) {
//                message.append(String.format(Locale.US, "%.3f: %s", label.getScore(), label.getDescription())); //분석결과 = label.getDescription()
//                message.append("\n");
                if (label.getDescription().equals("Face")) {
                    message.append("사람");
                } else if (label.getDescription().equals("Head")) {
                    message.append("사람");
                } else if (label.getDescription().equals("Arm")) {
                    message.append("사람");
                } else if (label.getDescription().equals("Forehead")) {
                    message.append("사람");
                } else if (label.getDescription().equals("Neck")) {
                    message.append("사람");
                } else if (label.getDescription().equals("Shoulder")) {
                    message.append("사람");
                } else if (label.getDescription().equals("Leg")) {
                    message.append("사람");
                } else if (label.getDescription().equals("Hand")) {
                    message.append("사람");
                } else if (label.getDescription().equals("Elbow")) {
                    message.append("사람");
                } else if (label.getDescription().equalsIgnoreCase("cat")) {
                    message.append("고양이");
                } else if (label.getDescription().equalsIgnoreCase("dog")) {
                    message.append("강아지");
                } else if (label.getScore() > 0.1) {
                    labeList.add(label.getDescription()); // 사람, 고양이, 강아지가 아닐 경우 분석결과를 리스트에 쌓는다.
                }

                if (SendThread.mHandler != null) {
                    if (label.getDescription().equals("Face")) {
                        Message msg = Message.obtain();
                        msg.what = SendThread.CMD_SEND_MESSAGE;
                        msg.obj = "Face";
                        SendThread.mHandler.sendMessage(msg);  //작성한 메시지를 SendThread로 핸들러를 이용해 보냄
                    } else if (label.getDescription().contains("Cat")) {
                        Message msg = Message.obtain();
                        msg.what = SendThread.CMD_SEND_MESSAGE;
                        msg.obj = "Cat";
                        SendThread.mHandler.sendMessage(msg);
                    } else if (label.getDescription().contains("Dog")) {
                        Message msg = Message.obtain();
                        msg.what = SendThread.CMD_SEND_MESSAGE;
                        msg.obj = "Dog";
                        SendThread.mHandler.sendMessage(msg);
                    }
                }
            }

            message.append(labeList.get(0)); // 분석결과 중 Score 가 높은 결과를 메세지에 쌓는다.

        } else {
            message.append("nothing");
        }

        return message.toString();
    }

    private Vision.Images.Annotate prepareAnnotationRequest(Bitmap bitmap) throws IOException {
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        Log.d("prepareAn", "prepareAnnotationRequest1");
        VisionRequestInitializer requestInitializer =
                new VisionRequestInitializer(CLOUD_VISION_API_KEY) {

                    /**
                     * We override this so we can inject important identifying fields into the HTTP
                     * headers. This enables use of a restricted cloud platform API key.
                     */

                    @Override
                    protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                            throws IOException {
                        super.initializeVisionRequest(visionRequest);

                        String packageName = getPackageName();
                        visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                        String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                        visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                    }
                };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(requestInitializer);

        Vision vision = builder.build();

        BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                new BatchAnnotateImagesRequest();
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            // Add the image
            Image base64EncodedImage = new Image();
            // Convert the bitmap to a JPEG
            // Just in case it's a format that Android understands but Cloud Vision
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Base64 encode the JPEG
            base64EncodedImage.encodeContent(imageBytes);
            annotateImageRequest.setImage(base64EncodedImage);

            // add the features we want
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature ObjectDetection = new Feature();
                ObjectDetection.setType("LABEL_DETECTION");
                ObjectDetection.setMaxResults(MAX_LABEL_RESULTS);
                add(ObjectDetection);
            }});


            // Add the list of one thing to the request
            add(annotateImageRequest);
        }});

        Vision.Images.Annotate annotateRequest =
                vision.images().annotate(batchAnnotateImagesRequest);
        // Due to a bug: requests to Vision API containing large images fail when GZipped.
        annotateRequest.setDisableGZipContent(true);
        Log.d(TAG, "created Cloud Vision request object, sending request22");

        return annotateRequest;
    }
}