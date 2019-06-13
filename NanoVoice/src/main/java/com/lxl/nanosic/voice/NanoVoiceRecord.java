package com.lxl.nanosic.voice;

import android.util.Log;
import com.sun.jna.Pointer;
import java.util.LinkedList;

public class NanoVoiceRecord {

    private final String TAG = "NanoVoiceRecord";
    /**
     * AudioRecord 写入缓冲区大小
     */
    protected int m_in_buf_size;

    /**
     * 存放录入字节数组的大小
     */
    private static LinkedList<byte[]> m_in_q = null;

    /**
     * 录音状态标志
     */
    private static boolean isRecording = false;

    /**
     * 回调函数
     */
    private static JnaNanovoice.AppCallback mDataCallback;

    /** API 1:录音状态 **/
    public boolean isRecording()
    {
        return  isRecording;
    }

    /** API 2:开始录音 **/
    public void start() {
        if (isRecording) {
            Log.w(TAG, "Nanosic device already is recording!");
            return;
        } else {
            isRecording = true;
        }

        // 实例化一个链表，用来存放字节组数
        if (m_in_q == null) {
            m_in_q = new LinkedList<byte[]>();
            Log.d(TAG, "Create audio buffer...");
        } else {
            //清除之前语音缓存
            m_in_q.clear();
            Log.d(TAG, "Clear audio buffer...");
        }

        // JNA接口
        mDataCallback = new JnaNanovoice.AppCallback() {
            // 实现接口中的回调
            public void dataReceived (Pointer data, int datalen){
                if(null != data){
                    byte[] buffer = data.getByteArray(0, datalen);
                    int res = OnDataReceived(buffer, datalen);
                    Log.w(TAG, "...OnDataReceived = " + res + "m_in_q.size=" + m_in_q.size());
                } else {
                    Log.e(TAG, "...Callback data is null !!!");
                }
            }
        };
        // 开始录音并传入回调
        JnaNanovoice.INSTANCE.nano_open(mDataCallback);
    }

    /** API 3:停止录音 **/
    public void stop()
    {
        JnaNanovoice.INSTANCE.nano_close(); //调用JNA接口退出录音
        isRecording=false;
    }

    /** API 4:数据读取 **/
    public int read(byte[] buffer, int maxSize)
    {
        byte[] bytes_pkg;

        if(buffer == null && m_in_q==null){
            Log.e(TAG,"The buffer is NULL!");
            return 0;
        }

        if(m_in_q.isEmpty()){
            Log.w(TAG, "........No data........");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else{
            bytes_pkg = m_in_q.getFirst(); // 取出
            m_in_q.removeFirst(); // 删除
            //Log.i(TAG,"m_in_q.size() : "+m_in_q.size());

            if(bytes_pkg.length > maxSize){
                Log.e(TAG,"The read buffer is too small!");
            }else{
                //浅拷贝
                System.arraycopy(bytes_pkg,0,buffer,0,bytes_pkg.length);
                //Log.i(TAG,"System.arraycopy : "+bytes_pkg.length);
                return bytes_pkg.length;
            }
        }

        return 0;
    }

    /**
     * 处理c层回调上来的遥控器语音数据方法
     */
    public int OnDataReceived(byte[] buffer, int size) {
        byte[] bytes_pkg;
        int bufferLength;

        if(m_in_q==null){
            return 0;
        }

        bytes_pkg = buffer.clone();

        if (m_in_q.size() > 6) { //最多缓存6个包
            m_in_q.removeFirst();
        }
        m_in_q.add(bytes_pkg);

        /*
        Log.w(TAG, "...OnDataReceived = " + bytes_pkg.length +
                "m_in_q.size="+m_in_q.size());
        */

        bufferLength = m_in_q.size()*bytes_pkg.length; // 包数*每包长=总数据量(bytes)
        return bufferLength;
    }
}