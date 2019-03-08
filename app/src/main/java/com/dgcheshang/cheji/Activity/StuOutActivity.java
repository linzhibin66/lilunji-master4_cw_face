package com.dgcheshang.cheji.Activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.chainway.facedet.FD_FSDKFace;
import com.chainway.facedet.FaceDetJni;
import com.dgcheshang.cheji.Database.DbHandle;
import com.dgcheshang.cheji.R;
import com.dgcheshang.cheji.Tools.FileUtil;
import com.dgcheshang.cheji.Tools.Helper;
import com.dgcheshang.cheji.Tools.LoadingDialogUtils;
import com.dgcheshang.cheji.Tools.Speaking;
import com.dgcheshang.cheji.netty.conf.NettyConf;
import com.dgcheshang.cheji.netty.po.Tdata;
import com.dgcheshang.cheji.netty.serverreply.SfrzR;
import com.dgcheshang.cheji.netty.serverreply.XydcR;
import com.dgcheshang.cheji.netty.timer.CardTimer;
import com.dgcheshang.cheji.netty.timer.LoadingTimer;
import com.dgcheshang.cheji.netty.tools.RfidUtil;
import com.dgcheshang.cheji.netty.util.ByteUtil;
import com.dgcheshang.cheji.netty.util.GatewayService;
import com.dgcheshang.cheji.netty.util.RlsbUtil;
import com.dgcheshang.cheji.netty.util.ZdUtil;
import com.rscja.deviceapi.RFIDWithISO14443A;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.UVCCameraTextureView;
import com.shenyaocn.android.Encoder.CameraRecorder;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 *学员登出页面
 */

public class StuOutActivity extends Activity implements View.OnClickListener,CameraDialog.CameraDialogParent{
    Context context =StuOutActivity.this;

    private String TAG="StuOutActivity";
    ImageView image_shuaka,image_zhiwen,image_shenfen;
    RFIDWithISO14443A mRFID;
    SharedPreferences sp;
    SharedPreferences zdcssp;
    float sbd;
    TextView tv_bianhao,tv_idcard,tv_carlx,tv_stu_name;
    BroadcastReceiver receiver;//下载广播
    Dialog loading;
    LoadingTimer loadingTimer;
    boolean isback=true;//是否可按返回键
    public String landmarks_path, facenet_path, train_path;
    Timer timer;
    SfrzR xyxx;//全局参数
    RfidUtil rfid = new RfidUtil();
    private final Object mSync = new Object();
    //摄像头使用参数
    private USBMonitor mUSBMonitor;					// 用于监视USB设备接入
    private UVCCamera mUVCCameraL;					// 表示左边摄像头设备
    private UVCCamera mUVCCameraR;					// 表示右边摄像头设备

    private OutputStream snapshotOutStreamL;		// 用于左边摄像头拍照
    private String snapshotFileNameL;
    private UVCCameraTextureView mUVCCameraViewL;	// 用于左边摄像头预览
    private Surface mLeftPreviewSurface;
    private static final int PREVIEW_WIDTH = 320;
    private static final int PREVIEW_HEIGHT = 240;
    private static final boolean DEBUG = true;
    private CameraRecorder mp4RecorderL=new CameraRecorder(1);
    private int currentWidth = UVCCamera.DEFAULT_PREVIEW_WIDTH;
    private int currentHeight = UVCCamera.DEFAULT_PREVIEW_HEIGHT;

    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
           if(msg.arg1==2){
                //学员登出
                synchronized (mSync) {
                    Bundle data = msg.getData();
                    XydcR xydcr = (XydcR) data.getSerializable("xydcr");//学员登录成功后返回来的数据
                    handleOut(xydcr);
                }
            }else if(msg.arg1==5){
                //读卡获取学员信息
                Bundle data = msg.getData();
                xyxx = (SfrzR) data.getSerializable("xyxx");
                getXyxx(xyxx);

            }else if(msg.arg1==6){//uid
               //读取uid成功返回
                String xyuid = msg.getData().getString("xyuid");
                String sql="select * from tsfrz where uuid=? and lx=?";
                String[] params={xyuid,"4"};
                ArrayList<SfrzR> list= DbHandle.queryTsfrz(sql,params);
                if(list.size()==0){
                    Speaking.in("无此学员登录信息");
                    //继续刷卡
                    CardTimer.isstop=false;
                }else{
                    image_shuaka.setBackgroundResource(R.mipmap.login_rid_xycard_y);
                    xyxx=list.get(0);
                    long sj = Long.valueOf(xyxx.getSj());//登录时间
                    byte theoryType = xyxx.getTheoryType();
                    long bdtime = ZdUtil.getLongTime();
                    long i = bdtime - sj;
                    long i1 = 4 * 3600 * 1000;
                    long i2 = 2 * 3600 * 1000;
                    if(theoryType==0){
                        if(i<i1){
                            //未满足4小时
                            chooseDialog("一","4");
                        }else {
                            NettyConf.xbh=xyxx.getTybh();
                            NettyConf.xydlsj=xyxx.getSj();
                            getXyxx(xyxx);
                        }
                    }else if(theoryType==1){
                        if(i<i2){
                            //未满足2小时
                            chooseDialog("四","2");
                        }else {
                            NettyConf.xbh=xyxx.getTybh();
                            NettyConf.xydlsj=xyxx.getSj();
                            getXyxx(xyxx);
                        }
                    }
//                    if(i<i1){
//                        //未满足4小时
//                        chooseDialog("一","2");
//                    }else {
//                        NettyConf.xbh=xyxx.getTybh();
//                        NettyConf.xydlsj=xyxx.getSj();
//                        getXyxx(xyxx);
//                    }
//
                }
            }else if(msg.arg1==7){//验证指纹成功后
               //学员登出
               image_zhiwen.setBackgroundResource(R.mipmap.login_fingerprint_y);

            }else if(msg.arg1==8){
                studentOut2();
            }else if(msg.arg1==9){
                //登出拍照
                studentOut1();
            }else if(msg.arg1==10){
               int stunum = msg.getData().getInt("stunum");
               if(stunum>0){
                   Speaking.in("学员登出成功，下一位");
               }else {
                   Speaking.in("学员已全部登出，教练请签退");
                   finish();
               }

           }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stuout);
        NettyConf.handlersmap.put("stuout",handler);
        initView();
        ZdUtil.ispz=true;
        if(NettyConf.cardtimer!=null){
            NettyConf.cardtimer.cancel();
            NettyConf.cardtimer=null;
        }
        try {
            mRFID = RFIDWithISO14443A.getInstance();
        }catch(Exception e){
        }
    }

    private void initView() {
        View layout_back = findViewById(R.id.layout_back);//返回按钮
        zdcssp = getSharedPreferences("zdcs", Context.MODE_PRIVATE);
        sbd=Float.parseFloat(zdcssp.getString("003F","0.96f"));
        //学员登录布局
        image_shuaka = (ImageView) findViewById(R.id.image_shuaka);//刷卡图片
        image_zhiwen = (ImageView) findViewById(R.id.image_zhiwen);//指纹图片
        image_shenfen = (ImageView) findViewById(R.id.image_shenfen);//身份图片
        tv_bianhao = (TextView) findViewById(R.id.tv_bianhao);//学员编号
        tv_idcard = (TextView) findViewById(R.id.tv_idcard);//身份证号
        tv_stu_name = (TextView) findViewById(R.id.tv_stu_name);//姓名
        tv_carlx = (TextView) findViewById(R.id.tv_carlx);//车型
        View layout_go = findViewById(R.id.layout_go);
        View layout_faceyz = findViewById(R.id.layout_faceyz);
        if(zdcssp.getBoolean("startface", false)==false){
            layout_go.setVisibility(View.GONE);
            layout_faceyz.setVisibility(View.GONE);
        }
        //摄像头
        mUVCCameraViewL = (UVCCameraTextureView)findViewById(R.id.camera_view_L);
        mUVCCameraViewL.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);
        layout_back.setOnClickListener(this);

    }

    /**
     * 点击监听
     * */
    @Override
    public void onClick(View v) {

        switch (v.getId()){
            case R.id.layout_back://返回
                finish();
                break;
        }
    }

    /**
     * 登出拍照
     */
    private void studentOut1() {
        if(NettyConf.startface==false){
            loading = LoadingDialogUtils.createLoadingDialog(context,"正在登出...");
            loadingTimer = new LoadingTimer(loading);
            timer = new Timer();
            timer.schedule(loadingTimer,NettyConf.controltime);
        }
//        ZdUtil.studentOut1();
        if(NettyConf.startface==true){
           //人脸验证
            ZdUtil.sendZpsc2("129", "0", "18","",bdpic);
        }else {

            final String path = captureSnapshot();
            final Timer ystimer = new Timer();
            TimerTask ystask=new TimerTask() {
                @Override
                public void run() {
                    ystimer.cancel();
                    ZdUtil.sendZpsc2("129", "0", "18","",path);
                }
            };
            ystimer.schedule(ystask,2000);
        }
        if(xyxx.getTheoryType()==1){
            FileUtil.deleteOneFile(NettyConf.jlyxy_picurl+xyxx.getSfzh()+".jpg");
        }

    }

    private void studentOut2(){
        List<Tdata> list=ZdUtil.studentOut2();

        if (ZdUtil.pdNetwork() && NettyConf.constate == 1 && NettyConf.jqstate == 1){
            GatewayService.sendHexMsgToServer("serverChannel",list);
        }else{
            DbHandle.insertTdatas(list,6);
            //改变学员登出状态
            XydcR xr=new XydcR();
            xr.setJg(1);
            xr.setXybh(xyxx.getTybh());
            handleOut(xr);
        }

    }

    /**
     * 登出处理
     * */
    public void handleOut(XydcR xydcr){
        //取消加载动画
        if(loadingTimer!=null){
            loadingTimer.cancel();
        }
        if(timer!=null) {
            timer.cancel();
        }

        if(xydcr.getJg()==1){
            //学员登出成功
            ZdUtil.handleStudentOut(xydcr.getXybh());

        }else {
            //登出失败
            Speaking.in("学员登出失败");
        }
        LoadingDialogUtils.closeDialog(loading);
        //继续刷卡登出；
        keepRid();
    }

    /**
     * 读卡成功后获取学员信息
     * */
    public void getXyxx(SfrzR xyxx){
        NettyConf.xbh=xyxx.getTybh();
        //获取信息成功后显示身份信息
        tv_bianhao.setText(xyxx.getTybh());
        tv_idcard.setText(xyxx.getSfzh());
        tv_stu_name.setText(xyxx.getXm());
        tv_carlx.setText(xyxx.getCx());

        //关闭刷卡
        mRFID.free();
        if(NettyConf.cardtimer!=null){
            NettyConf.cardtimer.cancel();
            NettyConf.cardtimer=null;
        }
        if(NettyConf.startface==true){
            commonstu(xyxx);
        }else {
            //直接无指纹验证进行登陆
            studentOut1();
        }

    }

    /**
     * 选择是否继续退出
     * */
    private void chooseDialog(String bf,String time) {
            final AlertDialog.Builder normalDialog = new AlertDialog.Builder(context);
            normalDialog.setTitle("提示");
            normalDialog.setMessage("您当前第"+bf+"部分培训时长未满"+time+"小时，确定要登出吗？");
            normalDialog.setPositiveButton("确定",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            NettyConf.xbh=xyxx.getTybh();
                            NettyConf.xydlsj=xyxx.getSj();
                            getXyxx(xyxx);
                        }
                    });
            normalDialog.setNegativeButton("取消",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            //继续刷卡
                            CardTimer.isstop=false;
                        }
                    });
            // 显示
            normalDialog.setCancelable(false);
            normalDialog.show();
    }

    /**
     * 继续刷卡
     * */
    public void keepRid(){
        image_shuaka.setBackgroundResource(R.mipmap.login_rid_xycard_n);
        image_zhiwen.setBackgroundResource(R.mipmap.login_face_n);
        image_shenfen.setBackgroundResource(R.mipmap.login_ok_n);

        //强制初始化刷卡
        mRFID.init();
        //启动刷卡
        CardTimer.isstop=false;
        CardTimer cardTimer=new CardTimer(mRFID, "xycardout");
        NettyConf.cardtimer=new Timer();
        NettyConf.cardtimer.schedule(cardTimer,2000,2000);
        //3分钟要关闭页面的定时器
        closeActivity();
    }

    /**
     * finishtimer   3分钟要关闭页面的定时器
     * */
    Timer finishtimer;
    public void closeActivity(){
        if(finishtimer!=null){
            finishtimer.cancel();
        }
        finishtimer = new Timer();
        TimerTask finishtask=new TimerTask() {
            @Override
            public void run() {
                finishtimer.cancel();
                finish();
            }
        };
        //2分钟后关闭登录页面
        finishtimer.schedule(finishtask,180*1000);
    }

    /**
     * 初始化人脸识别摄像头
     * */
    FaceDetJni FaceDet;
    List<FD_FSDKFace> faceResult;
    public void initrlCamera(){
        faceResult = new ArrayList<>();
        FaceDet = new FaceDetJni();
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
        mUSBMonitor.setDeviceFilter(filters);
        mUSBMonitor.register();//start
        refreshControls();

        if(NettyConf.startface==true){
            final Timer initbdtimer = new Timer();
            TimerTask task=new TimerTask() {
                @Override
                public void run() {
                    Log.e("TAG","进行初始化比对");
                    landmarks_path = RlsbUtil.getAssetsCacheFile(StuOutActivity.this,"face_landmarks_5_cilab.dat");
                    facenet_path = RlsbUtil.getAssetsCacheFile(StuOutActivity.this,"facenet_cilab.dat");
                    train_path =RlsbUtil.getAssetsCacheFile(StuOutActivity.this,"complex_training.txt");
                    FaceDet.FaceDetInit(landmarks_path, facenet_path, train_path);
                    initbdtimer.cancel();
                }
            };
            initbdtimer.schedule(task,20);
        }

//        showCamera();
    }

    /**
     * 人脸识别通道
     * type 分为login和out
     * */
    Timer rlsbtimer;
    public void commonstu(final SfrzR xyxx){

        isback=false;
        final String zp = xyxx.getZp();
        Log.e("TAG","学员下载图片路径："+zp);
        if(zp==null||zp.equals("")){
            Toast.makeText(context,"没有照片下载有效路径",Toast.LENGTH_SHORT).show();
            isback=true;
            keepRid();
            return;
        }
        Speaking.in("正在人脸验证，请对准摄像头");
        final String sfzh = xyxx.getSfzh();

        //判断文件夹是否存在
        RlsbUtil.isexistAndBuild(NettyConf.jlyxy_picurl);
        //学员原始照片路径
        final String xyzp=NettyConf.jlyxy_picurl+sfzh+".jpg";
        if(RlsbUtil.isFileExist(xyzp)==false){
            //没有教练照片去下载
//          String  zp1=new String(ByteUtil.hexStringToByte(zp));
//          Log.e("TAG","照片："+zp1);
            downFile(zp,sfzh,xyzp);
        }else {
            //有教练照片直接抓拍验证,传“have_pic”状态
            rlsb(xyzp,sfzh);
        }

//        rlsbtimer = new Timer();
//
//        TimerTask task=new TimerTask() {
//            @Override
//            public void run() {
//                rlsbtimer.cancel();
//                if(RlsbUtil.isFileExist(xyzp)==false){
//                    //没有教练照片去下载
////                    String  zp1=new String(ByteUtil.hexStringToByte(zp));
////                    Log.e("TAG","照片："+zp1);
//                    downFile(zp,sfzh,xyzp);
//                }else {
//                    //有教练照片直接抓拍验证,传“have_pic”状态
//                    rlsb(xyzp,sfzh);
//                }
//            }
//        };
//        rlsbtimer.schedule(task,2000);
    }


    /**
     * 下载文件
     * */
    int jisi=0;
    boolean save_ok=false;
    public void downFile(String url, final String sfzh, final String xyzp){

        //下载文件
        try {
            final DownloadManager dManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            // 设置下载路径和文件名
            request.setDestinationInExternalPublicDir("jlyxypic", sfzh+".jpg");
            request.setMimeType("image/jpeg");
            request.setAllowedOverRoaming(false);
            // 设置为可被媒体扫描器找到
            request.allowScanningByMediaScanner();
            // 设置为可见和可管理
            request.setVisibleInDownloadsUi(true);
            // 获取此次下载的ID
            final long refernece = dManager.enqueue(request);

            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

            receiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    long myDwonloadID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    //下载状态查询
//                DownloadManager.Query query = new DownloadManager.Query().setFilterById(refernece);
//                Cursor c = dManager.query(query);if (c != null && c.moveToFirst()) {
//                    int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
//                    switch (status) {
//                        case DownloadManager.STATUS_PENDING:
//                            break;
//                        case DownloadManager.STATUS_PAUSED:
//                            break;
//                        case DownloadManager.STATUS_RUNNING:
//                            break;
//                        case DownloadManager.STATUS_SUCCESSFUL:
//                            Log.e("TAG","下载成功");
//                            //下载完成操作，保存原照片 身份证号用来区别
//                            rlsb(jlzp, sfzh,type);
//                            break;
//                        case DownloadManager.STATUS_FAILED:
//                            Log.e("TAG","下载失败");
//                            Speaking.in("照片下载失败");
//                            break;
//                    }
//                    if (c != null) {
//                        c.close();
//                    }
//                }

                    if (refernece == myDwonloadID) {
                        //下载完成操作，保存原照片 身份证号用来区别
                        Log.e("TAG","下载教练照片成功");
                        if(RlsbUtil.isFileExist(xyzp)==true){
                            //照片存在

                            final Timer timer11 = new Timer();
                            TimerTask task11=new TimerTask() {
                                @Override
                                public void run() {
                                    if(jisi<30){
                                        //保存原始照片特征值
                                        save_ok=isfiled(xyzp, sfzh);
                                        if(save_ok==true){
                                            timer11.cancel();
                                            jisi=0;
                                            rlsb(xyzp, sfzh);
                                        }else {
                                            jisi++;
                                            Log.e("TAG","次数："+jisi);
                                        }

                                    }else {
                                        //保存原始照片特征值30秒，超过提示保存失败
                                        timer11.cancel();
                                        jisi=0;
                                        Speaking.in("照片特征值保存失败");
                                    }

                                }
                            };
                            timer11.schedule(task11,3000,1000);
                        }else {
                            //照片不存在
                            Log.e("TAG","学员照片下载失败1");
                            Speaking.in("学员照片下载失败");
                            keepRid();
                        }

                    }else {
                        isback=true;
                        Log.e("TAG","学员照片下载失败2");
                        Speaking.in("学员照片下载失败");
                        keepRid();
                    }
                }
            };
            registerReceiver(receiver, filter);
        }catch (Exception ex){
            isback=true;
            Log.e("TAG","学员照片下载失败3");
            Speaking.in("学员照片下载失败");
            keepRid();
        }

    }

    /**
     * 人脸识别成功后处理教练登录或教练登出
     * ishave_pic 判断是否有教练照片，有则无需保存特征值，无则保存特征值 true有照片，false没照片
     * */
    Timer pztimer;
    TimerTask pztask;
    int isfinishphoto = 0;//60秒停止比对

    public void rlsb(final String xyzp, final String sfzh){
        isback=true;
        //只有当原始照片保存成功才进行人脸识别
        if(isCameraL==true){
            pztimer = new Timer();
            pztask=new TimerTask() {
                @Override
                public void run() {
                    if(isfinishphoto<20){
                        isfinishphoto++;
                        String path = captureSnapshot();
                        boolean stopcamera=compare(path,sfzh);
                        if(stopcamera==true){
                            pztimer.cancel();
                            final Timer rlcltimer = new Timer();
                            TimerTask task=new TimerTask() {
                                @Override
                                public void run() {
                                    //登录处理
                                    rlcltimer.cancel();
                                    studentOut1();
                                }
                            };
                            rlcltimer.schedule(task,200);
                            RlsbUtil.addtimer(rlcltimer);
                        }
                    }else {
                        //如果超过60秒则自动关闭页面
                        pztimer.cancel();
                        Speaking.in("人脸识别失败");
                        keepRid();
                    }

                }
            };
            pztimer.schedule(pztask,200,3000);
            RlsbUtil.addtimer(pztimer);

        }else {
            //保存原始图片失败
            Speaking.in("人脸识别失败");
        }

    }

    /**
     * 比对照片返回结果
     * */
    String bdpic="";//比对成功后的照片
    public boolean compare( String newcameraurl,String sfzh) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (faceResult.size() > 0) {
            faceResult.clear();
        }
        Long filesize = FileUtil.fileLength(newcameraurl);
        if(filesize>10){
            boolean ret = FaceDet.FD_FSDK_FaceDetection(newcameraurl, faceResult);
            Log.e("TAG","获取抓拍照片轮廓结果=" + ret);
            if(ret==true &&faceResult!=null&&faceResult.size()>0){

                String MatchName = FaceDet.FaceDetectMuti(newcameraurl, sbd);
                Log.e("TAG","比对照片名字轮廓结果=" + MatchName);
                if(StringUtils.isNotEmpty(MatchName)){
                    bdpic=newcameraurl;
                    return true;
                }else {
                    boolean delete = RlsbUtil.delete(newcameraurl);
                    //filepath-->图片绝对路径
                    getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.DATA + "=?", new String[]{newcameraurl});
                    return false;
                }

            }else {
                boolean delete = RlsbUtil.delete(newcameraurl);
                //filepath-->图片绝对路径
                getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.DATA + "=?", new String[]{newcameraurl});
                return false;
            }
        }else {
            return false;
        }

    }

    /**
     * 判断文件是否存在并保存比对原照片
     * */
    public boolean isfiled(String mageurl,String name){
        if (faceResult.size() > 0) {
            faceResult.clear();
        }

        File file = new File(mageurl);
        if (file.exists()&&file.length() > 0) {
            boolean ret = FaceDet.FD_FSDK_FaceDetection(mageurl, faceResult);
            Log.e("TAG","获取原始照片轮廓结果=" + ret);
            if(ret==true){
                //成功获取轮廓并保存
                boolean mIsSave = FaceDet.CaptureFaceMuti(mageurl, name);
                Log.e("TAG","保存原始照片结果=" + mIsSave);

                return true;
            }else {
                return false;
            }
        }else {
            return false;
        }
    }


    /**
     *  实现快照抓取
     */
    private synchronized String captureSnapshot() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd.HH.mm.ss.SSSS");
        Date currentTime = new Date();

        //左边摄像头在使用
        snapshotFileNameL = Environment.getExternalStorageDirectory().getAbsolutePath() + "/chejiCamera";
        File path = new File(snapshotFileNameL);
        if (!path.exists())
            path.mkdirs();
        snapshotFileNameL += "/IPC_";
        snapshotFileNameL += format.format(currentTime);
        snapshotFileNameL += ".L.jpg";
        File recordFile = new File(snapshotFileNameL);	// 左边摄像头快照的文件名
        if(recordFile.exists()) {
            recordFile.delete();
        }
        try {
            boolean newFile = recordFile.createNewFile();
            snapshotOutStreamL = new FileOutputStream(recordFile);

        } catch (Exception e){
            Log.e("TAG",e.getMessage());
        }

        return snapshotFileNameL;

    }

    private synchronized void releaseCameraL() {
        synchronized (this) {

            if (mUVCCameraL != null) {
                try {
                    mUVCCameraL.setStatusCallback(null);
                    mUVCCameraL.setButtonCallback(null);
                    mUVCCameraL.close();
                    mUVCCameraL.destroy();
                } catch (final Exception e) {
                    Log.e(TAG, e.getMessage());
                } finally {
                    //Log.d(TAG, "*******releaseCameraL mUVCCameraL=null");
                    mUVCCameraL = null;
                }
            }
            if (mLeftPreviewSurface != null) {
                mLeftPreviewSurface.release();
                mLeftPreviewSurface = null;
            }
//			try {
//
//			if (mLeftPreviewSurface != null) {
//				mLeftPreviewSurface.release();
//				mLeftPreviewSurface = null;
//			}
//				}
//			 catch (final Exception e) {
//					Log.e(TAG, e.getMessage());
//			}finally{
//				mLeftPreviewSurface = null;
//				//Log.d(TAG, "*******releaseCameraL mLeftPreviewSurface=null");
//			}
        }
    }


    //判断是哪个摄像头在使用
    Boolean isCameraL =false;
    Boolean isCameraR =false;
    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            if (DEBUG) Log.i(TAG, "onAttach:" + device);
            final List<UsbDevice> list = mUSBMonitor.getDeviceList();
            mUSBMonitor.requestPermission(list.get(0));

            if(list.size() > 1)
                new Handler().postDelayed(new Runnable() {public void run() {mUSBMonitor.requestPermission(list.get(1));}}, 200);
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {

            if (DEBUG) Log.i(TAG, "onConnect:"+ctrlBlock.getVenderId());

            if(!NettyConf.camerastate) {
                NettyConf.camerastate = true;
//                Speaking.in("摄像头已开启");
            }
            if(loading!=null){
                loading.cancel();

            }
            synchronized (this) {
                if (mUVCCameraL != null && mUVCCameraR != null) { // 如果左右摄像头都打开了就不能再接入设备了
                    return;
                }
                if (ctrlBlock.getVenderId() == 2){

                    if (mUVCCameraL != null && mUVCCameraL.getDevice().equals(device)){
                        return;
                    }
                } else if (ctrlBlock.getVenderId() == 3) {

                    if ((mUVCCameraR != null && mUVCCameraR.getDevice().equals(device))) {
                        return;
                    }
                }else {
                    return;
                }
                final UVCCamera camera = new UVCCamera();
                final int open_camera_nums = (mUVCCameraL != null ? 1 : 0) + (mUVCCameraR != null ? 1 : 0);
                camera.open(ctrlBlock);

                try {
                    camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG, 0.5f); // 0.5f是一个重要参数，表示带宽可以平均分配给两个摄像头，如果是一个摄像头则是1.0f，可以参考驱动实现
                } catch (final IllegalArgumentException e1) {
                    if (DEBUG) Log.i(TAG, "MJPEG Failed");
                    try {
                        camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE, 0.5f);
                    } catch (final IllegalArgumentException e2) {
                        try {
                            currentWidth = UVCCamera.DEFAULT_PREVIEW_WIDTH;
                            currentHeight = UVCCamera.DEFAULT_PREVIEW_HEIGHT;
                            camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE, 0.5f);
                        } catch (final IllegalArgumentException e3) {
                            camera.destroy();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //Toast.makeText(ShowCameraViewActivity.this, "UVC设备错误", Toast.LENGTH_LONG).show();
                                }
                            });

                            return;
                        }
                    }
                }

                // 将摄像头进行分配
                if(ctrlBlock.getVenderId() == 2 ||ctrlBlock.getVenderId() == 3&& mUVCCameraL == null) {
                    isCameraL=true;
                    mUVCCameraL = camera;
                    try {
                        if (mLeftPreviewSurface != null) {
                            mLeftPreviewSurface.release();
                            mLeftPreviewSurface = null;
                        }

                        final SurfaceTexture st = mUVCCameraViewL.getSurfaceTexture();
                        if (st != null)
                            mLeftPreviewSurface = new Surface(st);
                        mUVCCameraL.setPreviewDisplay(mLeftPreviewSurface);

                        mUVCCameraL.setFrameCallback(mUVCFrameCallbackL, UVCCamera.PIXEL_FORMAT_YUV420SP);
                        mUVCCameraL.startPreview();
                    } catch (final Exception e) {
                        Log.e(TAG, e.getMessage());
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshControls();

                        //      if (mUVCCameraL != null || mUVCCameraR != null)
                        //      startAudio();
                    }
                });
            }
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) Log.i(TAG, "onDisconnect:" + device);
//            if(NettyConf.camerastate) {
//                NettyConf.camerastate = false;
//                Speaking.in("摄像头已断开");
//            }
		/*	runOnUiThread(new Runnable() {
				@Override
				public void run() {
					//refreshControls();
					if (mUVCCameraL == null && mUVCCameraR == null)
						stopAudio();
				}
			});*/
        }

        @Override
        public void onDettach(final UsbDevice device) {
            if (DEBUG) Log.i(TAG, "onDettach:" + device);
            if ((mUVCCameraL != null) && mUVCCameraL.getDevice().equals(device)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        releaseCameraL();
                    }
                });

            }

        }

        @Override
        public void onCancel(final UsbDevice device) {
            if (DEBUG) Log.i(TAG, "onCancel:");
        }
    };

    // 左边摄像头的NV21视频帧回调
    private final IFrameCallback mUVCFrameCallbackL = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {

            if(mUVCCameraL == null)
                return;

            final Size size = mUVCCameraL.getPreviewSize();
            byte[] buffer = null;

            int FrameSize = frame.remaining();
            if (buffer == null) {
                buffer = new byte[FrameSize];
                frame.get(buffer);
            }
            if (mp4RecorderL.isVideoRecord()) { // 将视频帧发送到编码器
                mp4RecorderL.feedData(buffer);
            }

            if(snapshotOutStreamL != null) { // 将视频帧压缩成jpeg图片，实现快照捕获
                if (!(FrameSize < size.width * size.height * 3 / 2) && (buffer != null)) {
                    try {
                        new YuvImage(buffer, ImageFormat.NV21, size.width, size.height, null).compressToJpeg(new Rect(0, 0, size.width, size.height), 60, snapshotOutStreamL);
                        snapshotOutStreamL.flush();
                        snapshotOutStreamL.close();
                        Helper.fileSavedProcess(StuOutActivity.this, snapshotFileNameL);
                    } catch (Exception ex) {
                    } finally {
                        snapshotOutStreamL = null;
                    }
                }
            }
            buffer = null;
        }
    };

    // 刷新UI控件状态
    private void refreshControls() {
        try {
            findViewById(R.id.textViewUVCPromptL).setVisibility(mUVCCameraL != null ? View.GONE : View.VISIBLE);
            invalidateOptionsMenu();
        } catch (Exception e){}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRFID!=null&&!mRFID.isPowerOn()) {
            mRFID.init();
            //启动刷卡
            CardTimer.isstop=false;
            CardTimer cardTimer=new CardTimer(mRFID, "xycardout");
            NettyConf.cardtimer=new Timer();
            NettyConf.cardtimer.schedule(cardTimer,2000,2000);
            Speaking.in("学员请刷卡");
        }
        refreshControls();
        initrlCamera();

    }

    @Override
    protected void onStop() {
        super.onStop();

        if(mRFID!=null){
            mRFID.free();
        }
        if(NettyConf.cardtimer!=null){
            NettyConf.cardtimer.cancel();
            NettyConf.cardtimer=null;
        }
        ZdUtil.ispz=false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //清除计时器
        if(timer!=null){
            timer.cancel();
        }
        if (mUSBMonitor != null) {
            releaseCameraL();
            mUSBMonitor.unregister();
        }
        if(NettyConf.cardtimer!=null){
            NettyConf.cardtimer.cancel();
            NettyConf.cardtimer=null;
        }
        if(mRFID!=null){
            mRFID.free();
        }
        ZdUtil.ispz=false;
        NettyConf.handlersmap.remove("stuout");
    }


    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    @Override
    public void onDialogResult(boolean b) {

    }
}
