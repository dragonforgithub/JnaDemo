#微纳语音包：NanoVoice-v1.0.aar （提供给客户使用）
#核心API:
(1) public boolean isRecording(); //获取当前录音状态
(2) public void start(); //开始录音
(3) public void stop(); //停止录音
(4) public int read(byte[] buffer, int maxSize); //读取语音数据

具体使用请参考 app 示例代码。