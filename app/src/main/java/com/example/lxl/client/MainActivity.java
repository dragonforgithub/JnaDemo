package com.example.lxl.client;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import static android.widget.Toast.LENGTH_SHORT;

import com.lxl.nanosic.voice.NanoVoiceRecord;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    public final String TAG = "APP_Client";

    /**
     * 录音按钮
     */
    private Button audioBtn;

    /**
     * 保存录音文件开关
     */
    private Button switchBtn;

    /**
     * 微纳录音接口
     */
    private NanoVoiceRecord mNanoVoiceRec = null;

    /**
     * AudioTrack 播放缓冲大小
     */
    private int m_out_buf_size;

    /**
     * 播放音频对象
     */
    private AudioTrack m_out_trk=null;

    /**
     * 播放的字节数组
     */
    private byte[] m_out_bytes;

    /**
     * 播放音频线程
     */
    private Thread m_play_thread;

    /**
     * 权限检查
     */
    private final int MY_PERMISSION_REQUEST_CODE = 10000;
    private final String[] strPermissions  = new String[] {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private String wavSavePath = "/sdcard/nanovoice_debug.wav";
    private boolean saveFile = false;
    private FileOutputStream wavOutSave = null;
    private int writeCnt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /** 判断SDK版本，确认是否动态申请权限 **/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /** 第 1 步: 检查是否有相应的权限 **/
            if(checkPermissionAllGranted(strPermissions)==false) {
                /** 第 2 步: 请求权限,一次请求多个权限, 如果其他有权限是已经授予的将会自动忽略掉 **/
                ActivityCompat.requestPermissions(
                        this,
                        strPermissions,
                        MY_PERMISSION_REQUEST_CODE
                );
            }

            /** 第 3 步: 判断权限申请结果，如用户未同意则引导至设置界面打开权限 **/
            int[] grantResults={0};
            onRequestPermissionsResult(MY_PERMISSION_REQUEST_CODE,strPermissions,grantResults);
        }

        /** 绑定按键监听 **/
        audioBtn = findViewById(R.id.audioBtn);
        audioBtn.setOnClickListener(new bTnOnClickListener());

        switchBtn = findViewById(R.id.switchBtn);
        switchBtn.setOnClickListener(new bTnOnClickListener());

        /** 微纳遥控器录音接口 **/
        mNanoVoiceRec = new NanoVoiceRecord();

        /** 初始化播放器 **/
        audioDevInit();
    }

    protected void onDestroy() {
        super.onDestroy();

        if(wavOutSave!=null){
            try {
                wavOutSave.close();
                wavOutSave=null;
                writeCnt=0;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(mNanoVoiceRec !=null){
            mNanoVoiceRec.stop();
            mNanoVoiceRec=null;
        }
        Log.i(TAG, "onDestroy() is called");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart() is called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop() is called");
    }

    /**
     * Toast提示
     */
    private void alert(String str) {
        //Looper.prepare();
        Toast.makeText(this, str, LENGTH_SHORT).show();
        //Looper.loop();
    }

    /**
     * 检查是否拥有指定的所有权限
     */
    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // 只要有一个权限没有被授予, 则直接返回 false
                return false;
            }
        }
        return true;
    }

    /**
     * 打开 APP 的详情设置
     */
    private void openAppDetails() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("请到 “应用信息 -> 权限” 中授予！");
        builder.setPositiveButton("去手动授权", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 申请权限结果返回处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MY_PERMISSION_REQUEST_CODE) {
            boolean isAllGranted = true;

            // 判断是否所有的权限都已经授予了
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false;
                    break;
                }
            }

            if (!isAllGranted) {
                // 弹出对话框告诉用户需要权限的原因, 并引导用户去应用权限管理中手动打开权限按钮
                openAppDetails();
            }
        }
    }

    /** 初始化 AudioTrack **/
    void audioDevInit()
    {
        // 默认不保存录音
        saveFile = false;

        // AudioTrack 得到播放最小缓冲区的大小
        m_out_buf_size = AudioTrack.getMinBufferSize(16000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        // 实例化播放音频对象
        if(m_out_trk==null){
            m_out_trk = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                    AudioFormat.CHANNEL_CONFIGURATION_DEFAULT,
                    AudioFormat.ENCODING_DEFAULT, m_out_buf_size,
                    AudioTrack.MODE_STREAM);
        }

        // 实例化一个长度为播放最小缓冲大小的字节数组
        m_out_bytes = new byte[m_out_buf_size];
    }

    /** 按键监听 **/
    private class bTnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.audioBtn:
                    if(mNanoVoiceRec != null){
                        if(mNanoVoiceRec.isRecording()){
                            mNanoVoiceRec.stop();
                            audioBtn.setText("开始录播>>");

                            if(wavOutSave != null){
                                try {
                                    wavOutSave.close();
                                    wavOutSave=null;
                                    alert("保存路径："+wavSavePath+"，大小："+writeCnt);
                                    writeCnt=0;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                        }else{

                            if(saveFile && wavOutSave==null){
                                Log.d(TAG, "new FileOutputStream");
                                try {
                                    wavOutSave = new FileOutputStream(wavSavePath);
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                }
                            }

                            mNanoVoiceRec.start();
                            audioBtn.setText("停止录播||");

                            // 创建播放线程
                            m_play_thread = new Thread(new playSound());
                            m_play_thread.start();
                        }
                    }
                    break;

                case R.id.switchBtn:
                    saveFile = !saveFile;
                    if(saveFile)
                        alert("开启录音文件保存！");
                    else
                        alert("关闭录音文件保存！");

                    break;

                default:
                    break;
            }
        }
    }

    /** 播放线程 **/
    class playSound implements Runnable
    {
        @Override
        public void run()
        {
            // TODO Auto-generated method stub
            Log.i(TAG, "........playSound run()......");

            // 开始播放
            m_out_trk.play();

            while (mNanoVoiceRec!=null && mNanoVoiceRec.isRecording())
            {
                try {

                    byte[] bytes_pkg = new byte[1024]; //分配的回调buffer

                    // 读取遥控器语音数据,返回实际数据长度
                    int size = mNanoVoiceRec.read(bytes_pkg,1024);

                    //Log.d(TAG, "........read & write...... : " + size);

                    // 使用AudioTrack播放
                    m_out_trk.write(bytes_pkg, 0, size);

                    //保存wav数据
                    if(wavOutSave != null){
                        wavOutSave.write(bytes_pkg, 0, size);
                        writeCnt += bytes_pkg.length;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}