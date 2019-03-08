package com.dgcheshang.cheji.Activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dgcheshang.cheji.Bean.database.StudentBean;
import com.dgcheshang.cheji.CjApplication;
import com.dgcheshang.cheji.Database.DbHandle;
import com.dgcheshang.cheji.R;
import com.dgcheshang.cheji.Tools.Helper;
import com.dgcheshang.cheji.Tools.LoadingDialogUtils;
import com.dgcheshang.cheji.Tools.Speaking;
import com.dgcheshang.cheji.netty.conf.NettyConf;
import com.dgcheshang.cheji.netty.timer.LoadingTimer;
import com.dgcheshang.cheji.netty.util.ZdUtil;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.UVCCameraTextureView;
import com.shenyaocn.android.Encoder.CameraRecorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * 学员登录
 * */
public class LoginStudentActivity extends Activity implements View.OnClickListener{

    Context context=LoginStudentActivity.this;
    private String TAG="LoginStudentActivity";
    public static final int LOGIN_STU_SUCCESS = 1;
    ArrayList<StudentBean> studentlist;
    TextView tv_kechen;
    ListView listview;
    String yzmm;
    Dialog loading;
    TextView tv_coachname,loginnum;
    int isqiantui=0;//强退状态,0表示未强退，1表示强退
    View layout_showphoto;
    SoundPool soundPool;

    private final Object mSync = new Object();
    //摄像头参数
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

    Handler handler=new  Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.arg1==10){
                //强制登出验证返回结果
                int yzjg = msg.getData().getInt("yzjg");
                if(yzjg==0){
                    /*if(!ZdUtil.ispz){
                        //学员登出
                        ZdUtil.qzStuOut();
                    }else {
                        Toast.makeText(context,",正在拍照请稍后操作",Toast.LENGTH_SHORT).show();
                        loading.cancel();
                    }*/
                    isqiantui=1;
                    myAdapter.notifyDataSetChanged();

                }else {
                    loading.cancel();
                    Speaking.in("密码验证失败");
                }
            }else if(msg.arg1==11){
                finish();
            }else if(msg.arg1==12){
                Speaking.in("拍照成功");
                layout_showphoto.setVisibility(View.GONE);
            }else if(msg.arg1==13){
                studentlist = DbHandle.stuQuery();
                int stunum = msg.getData().getInt("stunum");
                loginnum.setText(stunum+"");
                if(loading!=null){
                    loading.cancel();
                }
                myAdapter.notifyDataSetChanged();
                if(stunum>0){
                    Speaking.in("学员登出成功");
                }else {
                    Speaking.in("学员已全部登出，教练请签退");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_student);
        NettyConf.handlersmap.put("loginstudent",handler);
        initView();
        //拍照秒提示嘀嘀声初始化
        soundPool= new SoundPool(10, AudioManager.STREAM_SYSTEM,5);
        soundPool.load(CjApplication.getInstance(), R.raw.didi4,1);
    }

    /**
     * 初始化布局
     * */
    MyAdapter myAdapter;
    private void initView() {
        SharedPreferences coachsp = getSharedPreferences("coach", Context.MODE_PRIVATE);//教练保存的数据
        View layout_back = findViewById(R.id.layout_back);
        tv_kechen = (TextView) findViewById(R.id.tv_kechen);//选择课程显示
        listview = (ListView) findViewById(R.id.listview);//学员登陆列表
        Button bt_login = (Button) findViewById(R.id.bt_login);//登录
        Button bt_out = (Button) findViewById(R.id.bt_out);//登录
        Button bt_tongpai = (Button) findViewById(R.id.bt_tongpai);//统拍
        View layout_qzout = findViewById(R.id.layout_qzout);//强制登出
        tv_coachname = (TextView) findViewById(R.id.tv_coachname);//教练姓名
        loginnum = (TextView) findViewById(R.id.loginnum);//登录个数
        layout_showphoto = findViewById(R.id.layout_showphoto);//显示拍照框
        layout_showphoto.setVisibility(View.INVISIBLE);
        Button bt_pz_no = (Button) findViewById(R.id.bt_pz_no);
        Button bt_pz_yes = (Button) findViewById(R.id.bt_pz_yes);
        if(NettyConf.jlstate!=0){
            tv_kechen.setText(coachsp.getString("xzkc",""));
            tv_coachname.setText(coachsp.getString("jlxm",""));
        }

        //摄像头
        mUVCCameraViewL = (UVCCameraTextureView)findViewById(R.id.camera_view_L);
        mUVCCameraViewL.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);
        refreshControls();
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
        mUSBMonitor.setDeviceFilter(filters);
//        mUSBMonitor.register();//start
//        refreshControls();

        layout_qzout.setOnClickListener(this);
        layout_back.setOnClickListener(this);
        bt_login.setOnClickListener(this);
        bt_out.setOnClickListener(this);
        bt_tongpai.setOnClickListener(this);
        bt_pz_no.setOnClickListener(this);
        bt_pz_yes.setOnClickListener(this);
    }

    /**
     * 点击监听
     * */
    @Override
    public void onClick(View view) {
        Intent intent = new Intent();

        switch (view.getId()){
            case R.id.layout_back://返回
                finish();
                break;

            case R.id.bt_login://登录
                if(ZdUtil.canLogin()) {
                    intent.setClass(context, StuLoginActivity.class);
//                    startActivityForResult(intent, REQUEST_A);
                    startActivity(intent);
                }
                break;

            case R.id.bt_out://登出
                intent.setClass(context,StuOutActivity.class);
//                startActivityForResult(intent,REQUEST_B);
                startActivity(intent);
                break;

            case R.id.layout_qzout://强制登出
                showliuyanDialog();
                break;

            case R.id.bt_tongpai://统一拍照
                if(studentlist.size()>0){
                    layout_showphoto.setVisibility(View.VISIBLE);
                    if(mUSBMonitor!=null){
                        //注册了
                        releaseCameraL();
                        mUSBMonitor.unregister();
                    }
                    mUSBMonitor.register();
                }else {
                    Toast.makeText(context,"暂无学员登录",Toast.LENGTH_SHORT).show();
                }

                break;
            case R.id.bt_pz_no://取消拍照
                //关闭摄像头
                if(mUSBMonitor.isRegistered()){
                    //注册了
                    releaseCameraL();
                    mUSBMonitor.unregister();
                }
                layout_showphoto.setVisibility(View.INVISIBLE);
                break;

            case R.id.bt_pz_yes://拍照
                //拍照提前发出滴滴声
                soundPool.play(1,1, 1, 0, 0, 1);
                final String path = captureSnapshot();
                if(NettyConf.debug){
                    Log.e("TAG","照片路径："+path);
                }
                final Timer timer = new Timer();
                TimerTask task=new TimerTask() {
                    @Override
                    public void run() {
                        timer.cancel();
                        ZdUtil.sendZpsc2("129", "0", "5","",path);
                        //  关闭摄像头
                        releaseCameraL();
                        mUSBMonitor.unregister();
                        Message msg = new Message();
                        msg.arg1=12;
                        handler.sendMessage(msg);
                    }
                };
                timer.schedule(task,3000);

                break;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if(NettyConf.debug){
            Log.e("TAG","onResume");
        }
        //显示登录列表
        studentlist = DbHandle.stuQuery();
        loginnum.setText(studentlist.size()+"");
        Collections.reverse(studentlist);
        myAdapter = new MyAdapter();
        listview.setAdapter(myAdapter);
    }

    /**
     * 已登录学员Adapter
     * */
    public class MyAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            if(studentlist!=null){
                return studentlist.size();
            }else {
                return 0;
            }
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            ViewHodler viewHodler=null;
            if(convertView==null){
                convertView= LayoutInflater.from(context).inflate(R.layout.stulist2_item,null);
                viewHodler = new ViewHodler();
                viewHodler.tv_name = (TextView) convertView.findViewById(R.id.tv_name);//姓名
                viewHodler.bt_only_out = (Button) convertView.findViewById(R.id.bt_only_out);//登出按钮
                viewHodler.tv_idcard = (TextView) convertView.findViewById(R.id.tv_idcard);//身份证
                viewHodler.tv_logintime = (TextView) convertView.findViewById(R.id.tv_logintime);//登录时间
                viewHodler.bt_paizhao = (Button) convertView.findViewById(R.id.bt_paizhao);//拍照

                convertView.setTag(viewHodler);
            }else {
                viewHodler = (ViewHodler) convertView.getTag();
            }
            final StudentBean studentbean = studentlist.get(position);
            String xm = studentbean.getXm();
            if(isqiantui==0){
                viewHodler.bt_only_out.setVisibility(View.GONE);
            }else {
                viewHodler.bt_only_out.setVisibility(View.VISIBLE);
            }
            final String sfzh = studentbean.getSfzh();
            String tybh = studentbean.getTybh();
            String sj=studentbean.getSj();
            SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sj=sdf.format(new Date(Long.valueOf(sj)));
            viewHodler.tv_name.setText(xm);
            viewHodler.tv_idcard.setText(sfzh);
            viewHodler.tv_logintime.setText(sj);
            //退出按钮监听
            viewHodler.bt_only_out.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NettyConf.xbh=studentbean.getTybh();
                    NettyConf.xydlsj=studentbean.getSj();
                    studentOut1();
                }
            });
            //拍照
            viewHodler.bt_paizhao.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NettyConf.xbh=studentbean.getTybh();
                    ZdUtil.sendZpsc("129", "0", "19");
                }
            });
            return convertView;
        }
    }

    class ViewHodler{
        TextView tv_name;
        Button bt_only_out;
        Button bt_paizhao;
        TextView tv_idcard;
        TextView tv_logintime;

    }

    /**
     * 登出拍照
     */
    private void studentOut1() {
        loading = LoadingDialogUtils.createLoadingDialog(context,"正在登出...");
        LoadingTimer loadingTimer = new LoadingTimer(loading);
        Timer timer = new Timer();
        timer.schedule(loadingTimer,NettyConf.controltime);
        ZdUtil.sendZpsc("129", "0", "18");
//        final Timer ystimer = new Timer();
//        TimerTask ystask=new TimerTask() {
//            @Override
//            public void run() {
//                ystimer.cancel();
//
//            }
//        };
//        ystimer.schedule(ystask,2000);
    }

    /**
     * 强制登出dialog
     *
     * */

    private void showliuyanDialog(){
        final AlertDialog builder = new AlertDialog.Builder(this,R.style.CustomDialog).create(); // 先得到构造器
        builder.show();
        builder.getWindow().setContentView(R.layout.dialog_appoint_edt);
        builder.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);//解决不能弹出键盘
        LayoutInflater factory = LayoutInflater.from(this);
        View view = factory.inflate(R.layout.dialog_appoint_edt, null);
        builder.getWindow().setContentView(view);
        final EditText edt_content = (EditText) view.findViewById(R.id.edt_content);
        TextView tv_title = (TextView) view.findViewById(R.id.tv_title);
        Button bt_cacnel = (Button) view.findViewById(R.id.bt_cacnel);
        Button bt_sure = (Button) view.findViewById(R.id.bt_sure);
        tv_title.setText("登出验证");

        //取消
        bt_cacnel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                builder.dismiss();
            }
        });

        //确定
        bt_sure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                yzmm = edt_content.getText().toString().trim();
                if(!yzmm.equals("")){
//                    loading = LoadingDialogUtils.createLoadingDialog(context, "正在登出...");
                    ZdUtil.matchPassword(4,yzmm);
                    builder.dismiss();
                }else {
                    Toast.makeText(context,"请输入登出密码",Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * 统一拍照dialog
     * */
    private void showTongpaiDialog(){
        final AlertDialog builder = new AlertDialog.Builder(this,R.style.CustomDialog).create(); // 先得到构造器
        builder.show();
        builder.getWindow().setContentView(R.layout.dialog_text);
        builder.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);//解决不能弹出键盘
        LayoutInflater factory = LayoutInflater.from(this);
        View view = factory.inflate(R.layout.dialog_text, null);
        builder.getWindow().setContentView(view);
        TextView text_content = (TextView) view.findViewById(R.id.text_content);
        TextView tv_title = (TextView) view.findViewById(R.id.tv_title);
        Button bt_cacnel = (Button) view.findViewById(R.id.bt_cacnel);
        Button bt_sure = (Button) view.findViewById(R.id.bt_sure);
        tv_title.setText("提示");
        text_content.setText("是否进行统一拍照？");

        //取消
        bt_cacnel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                builder.dismiss();
            }
        });

        //确定
        bt_sure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //拍一张照上传
                ZdUtil.sendZpsc("129", "0", "5");
                builder.dismiss();
            }
        });
    }

    /**
     * 实现快照抓取
     * */
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
                        Helper.fileSavedProcess(LoginStudentActivity.this, snapshotFileNameL);
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
    protected void onStop() {
        super.onStop();
        if (mUSBMonitor != null) {
            releaseCameraL();
            mUSBMonitor.unregister();

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUSBMonitor != null) {
            releaseCameraL();
            mUSBMonitor.unregister();

        }
        NettyConf.handlersmap.remove("loginstudent");
    }

}
