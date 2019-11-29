#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

using namespace cv;
using namespace std;

float resize(Mat img_src, Mat &img_resize, int resize_width) {


    float scale = resize_width / (float) img_src.cols;

    if (img_src.cols > resize_width) {

        int new_height = cvRound(img_src.rows * scale);

        resize(img_src, img_resize, Size(resize_width, new_height));

    } else {

        img_resize = img_src;

    }

    return scale;

}

double real_facesize_x;
double real_facesize_y;
double real_facesize_width;
double real_facesize_height;

double faceX;
double faceY;
double faceWidth;
double faceHeight;

double currentFaceSize;
double previousSize;

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_vision03_MainActivity_loadCascade(JNIEnv *env, jobject instance,
                                                   jstring cascadeFileName_) {
    // const char *cascadeFileName = env->GetStringUTFChars(cascadeFileName_, 0);

    const char *nativeFileNameString = env->GetStringUTFChars(cascadeFileName_, 0);


    string baseDir("/storage/emulated/0/");

    baseDir.append(nativeFileNameString);

    const char *pathDir = baseDir.c_str();


    jlong ret = 0;

    ret = (jlong) new CascadeClassifier(pathDir);

    if (((CascadeClassifier *) ret)->empty()) {

        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",

                            "CascadeClassifier로 로딩 실패  %s", nativeFileNameString);

    } else

        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",

                            "CascadeClassifier로 로딩 성공 %s", nativeFileNameString);


    env->ReleaseStringUTFChars(cascadeFileName_, nativeFileNameString);


    return ret;

    //env->ReleaseStringUTFChars(cascadeFileName_, cascadeFileName);
}

extern "C"
JNIEXPORT jint JNICALL  //detect 코드
Java_com_example_vision03_MainActivity_detect(JNIEnv *env, jobject instance,
                                              jlong cascadeClassifier_face,
                                              jlong cascadeClassifier_eye,
                                              jlong matAddrInput, jlong matAddrResult) {
    Mat &img_input = *(Mat *) matAddrInput;

    Mat &img_result = *(Mat *) matAddrResult;

    int ret = 0;  //detect시 리턴값 저장할 변수


    img_result = img_input.clone();

    std::vector<Rect> faces;

    Mat img_gray;



    cvtColor(img_input, img_gray, COLOR_BGR2GRAY);

    equalizeHist(img_gray, img_gray);


    Mat img_resize;

    float resizeRatio = resize(img_gray, img_resize, 640);


    //-- Detect faces

    ((CascadeClassifier *) cascadeClassifier_face)->detectMultiScale(img_resize, faces, 1.1, 2,
                                                                     0 | CASCADE_SCALE_IMAGE,
                                                                     Size(30, 30));

    // 로그값 혼동을 방지하기 위해 주석처리
    //__android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
    //                   (char *) "face %d found ", faces.size());

    ret = faces.size();   //검출된 얼굴 개수 ret에 대입


    for (int i = 0; i < faces.size(); i++) {  //0번 얼굴부터 ret번 얼굴까지

        real_facesize_x = faces[i].x / resizeRatio;  //double 없앴음

        real_facesize_y = faces[i].y / resizeRatio;  //double 없앴음

        real_facesize_width = faces[i].width / resizeRatio;  //폭, 너비

        real_facesize_height = faces[i].height / resizeRatio;  //높이


        // ******* face 사이즈 측정
        currentFaceSize = real_facesize_width * real_facesize_height;

        if(i == 0) {
            previousSize = currentFaceSize;
            faceX = real_facesize_x;
            faceY = real_facesize_y;
            faceWidth = real_facesize_width;
            faceHeight = real_facesize_height;
        } else if(i > 0) {
            if(previousSize < currentFaceSize) {
                faceX = real_facesize_x;
                faceY = real_facesize_y;
                faceWidth = real_facesize_width;
                faceHeight = real_facesize_height;
            } else if(previousSize > currentFaceSize) {

            }
        } // *********

        Point center(real_facesize_x + real_facesize_width / 2,
                     real_facesize_y + real_facesize_height / 2);  //얼굴중심

        ellipse(img_result, center, Size(real_facesize_width / 2, real_facesize_height / 2), 0, 0,
                360,  //타원

                Scalar(255, 0, 255), 5, 20, 0);  //초기 30,8,0



        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                       real_facesize_height);  //사각형

        Mat faceROI = img_gray(face_area);

        std::vector<Rect> eyes;


        //-- In each face, detect eyes

        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale(faceROI, eyes, 1.1, 2, 0 | CASCADE_SCALE_IMAGE, Size(30, 30));


        /* for ( size_t j = 0; j < eyes.size(); j++ )

         {

             Point eye_center( real_facesize_x + eyes[j].x + eyes[j].width/2, real_facesize_y + eyes[j].y + eyes[j].height/2 );

             int radius = cvRound( (eyes[j].width + eyes[j].height)*0.25 );

             circle( img_result, eye_center, radius, Scalar( 255, 0, 0 ), 5, 20, 0 );

         }*/
    }

    return ret;
}

extern "C"
JNIEXPORT jdouble JNICALL  // 얼굴 가로 길이 코드
Java_com_example_vision03_MainActivity_faceWidth(JNIEnv *env, jobject instance,
                                                jlong cascadeClassifier_face,
                                                jlong cascadeClassifier_eye,
                                                jlong matAddrInput, jlong matAddrResult) {
    return faceX + faceWidth / 2;

}

extern "C"
JNIEXPORT jdouble JNICALL  // 얼굴 세로 길이 코드
Java_com_example_vision03_MainActivity_faceHeight(JNIEnv *env, jobject instance,
                                                 jlong cascadeClassifier_face,
                                                 jlong cascadeClassifier_eye,
                                                 jlong matAddrInput, jlong matAddrResult) {

    return faceY + faceHeight / 2;
}



extern "C"
JNIEXPORT jdouble JNICALL  //detect 코드
Java_com_example_vision03_MainActivity_detect4(JNIEnv *env, jobject instance,
                                               jlong cascadeClassifier_face,
                                               jlong cascadeClassifier_eye,
                                               jlong matAddrInput, jlong matAddrResult) {

    return real_facesize_x + real_facesize_width / 2;
}

extern "C"
JNIEXPORT jdouble JNICALL  //detect 코드
Java_com_example_vision03_MainActivity_detect5(JNIEnv *env, jobject instance,
                                               jlong cascadeClassifier_face,
                                               jlong cascadeClassifier_eye,
                                               jlong matAddrInput, jlong matAddrResult) {

    return real_facesize_y + real_facesize_height / 2;
}


/*

extern "C"
JNIEXPORT jdouble JNICALL  //detect 코드
Java_com_example_lenovo_opencvcmake_MainActivity_detect2(JNIEnv *env, jobject instance,
                                                        jlong cascadeClassifier_face,
                                                        jlong cascadeClassifier_eye,
                                                        jlong matAddrInput, jlong matAddrResult) {
    Mat &img_input = *(Mat *) matAddrInput;

    Mat &img_result = *(Mat *) matAddrResult;

    double real_facesize_x = 0;  //detect시 리턴값 저장할 변수

    double real_facesize_y = 0;

    double real_facesize_width = 0;  //폭, 너비

    double real_facesize_height = 0;  //높이

    img_result = img_input.clone();

    std::vector<Rect> faces;

    Mat img_gray;


    cvtColor(img_input, img_gray, COLOR_BGR2GRAY);

    equalizeHist(img_gray, img_gray);


    Mat img_resize;

    float resizeRatio = resize(img_gray, img_resize, 640);


    //-- Detect faces

    ((CascadeClassifier *) cascadeClassifier_face)->detectMultiScale( img_resize, faces, 1.1, 2, 0|CASCADE_SCALE_IMAGE, Size(30,30));


    for (int i = 0; i < faces.size(); i++) {  //0번 얼굴부터 ret번 얼굴까지

        real_facesize_x = faces[i].x / resizeRatio;

        real_facesize_y = faces[i].y / resizeRatio;

        real_facesize_width = faces[i].width / resizeRatio;  //폭, 너비

        real_facesize_height = faces[i].height / resizeRatio;  //높이


        Point center( real_facesize_x + real_facesize_width / 2, real_facesize_y + real_facesize_height/2);  //얼굴중심

        ellipse(img_result, center, Size( real_facesize_width / 2, real_facesize_height / 2), 0, 0, 360,  //타원

                Scalar(255, 0, 255), 5, 20, 0);  //초기 30,8,0



        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,real_facesize_height);  //사각형

        Mat faceROI = img_gray( face_area );

        std::vector<Rect> eyes;


        //-- In each face, detect eyes

        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale( faceROI, eyes, 1.1, 2, 0 |CASCADE_SCALE_IMAGE, Size(30, 30) );


        */
/* for ( size_t j = 0; j < eyes.size(); j++ )

         {

             Point eye_center( real_facesize_x + eyes[j].x + eyes[j].width/2, real_facesize_y + eyes[j].y + eyes[j].height/2 );

             int radius = cvRound( (eyes[j].width + eyes[j].height)*0.25 );

             circle( img_result, eye_center, radius, Scalar( 255, 0, 0 ), 5, 20, 0 );

         }*//*

    }

    return real_facesize_x;
}


extern "C"
JNIEXPORT jdouble JNICALL  //detect 코드
Java_com_example_lenovo_opencvcmake_MainActivity_detect3(JNIEnv *env, jobject instance,
                                                         jlong cascadeClassifier_face,
                                                         jlong cascadeClassifier_eye,
                                                         jlong matAddrInput, jlong matAddrResult) {
    Mat &img_input = *(Mat *) matAddrInput;

    Mat &img_result = *(Mat *) matAddrResult;

    double real_facesize_x = 0;  //detect시 리턴값 저장할 변수

    double real_facesize_y = 0;

    double real_facesize_width = 0;  //폭, 너비

    double real_facesize_height = 0;  //높이

    img_result = img_input.clone();

    std::vector<Rect> faces;

    Mat img_gray;


    cvtColor(img_input, img_gray, COLOR_BGR2GRAY);

    equalizeHist(img_gray, img_gray);


    Mat img_resize;

    float resizeRatio = resize(img_gray, img_resize, 640);


    //-- Detect faces

    ((CascadeClassifier *) cascadeClassifier_face)->detectMultiScale( img_resize, faces, 1.1, 2, 0|CASCADE_SCALE_IMAGE, Size(30,30));


    for (int i = 0; i < faces.size(); i++) {  //0번 얼굴부터 ret번 얼굴까지

        real_facesize_x = faces[i].x / resizeRatio;

        real_facesize_y = faces[i].y / resizeRatio;

        real_facesize_width = faces[i].width / resizeRatio;  //폭, 너비

        real_facesize_height = faces[i].height / resizeRatio;  //높이


        Point center( real_facesize_x + real_facesize_width / 2, real_facesize_y + real_facesize_height/2);  //얼굴중심

        ellipse(img_result, center, Size( real_facesize_width / 2, real_facesize_height / 2), 0, 0, 360,  //타원

                Scalar(255, 0, 255), 5, 20, 0);  //초기 30,8,0



        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,real_facesize_height);  //사각형

        Mat faceROI = img_gray( face_area );

        std::vector<Rect> eyes;


        //-- In each face, detect eyes

        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale( faceROI, eyes, 1.1, 2, 0 |CASCADE_SCALE_IMAGE, Size(30, 30) );


        */
/* for ( size_t j = 0; j < eyes.size(); j++ )

         {

             Point eye_center( real_facesize_x + eyes[j].x + eyes[j].width/2, real_facesize_y + eyes[j].y + eyes[j].height/2 );

             int radius = cvRound( (eyes[j].width + eyes[j].height)*0.25 );

             circle( img_result, eye_center, radius, Scalar( 255, 0, 0 ), 5, 20, 0 );

         }*//*

    }

    return real_facesize_y;
}

*/
