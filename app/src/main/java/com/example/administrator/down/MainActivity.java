package com.example.administrator.down;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    private Context mContext;
    private EditText  mUrlEditText;
    private ProgressBar mProgressBar;
    private Button mDownLoadButton;
    private TextView mPercentTextView;
    private UIHandler mHandler=new UIHandler();
    private final int NORMAL=9527;
    private final int ERROR=250;
    private final String MESSAGE_KEY="size";
    private DownloadProgressListener mDownloadProgressListener;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        mContext = this;
        mUrlEditText = (EditText) findViewById(R.id.urlEditText);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mPercentTextView = (TextView) findViewById(R.id.percentTextView);
        mDownLoadButton = (Button) findViewById(R.id.downloadButton);
        mDownLoadButton.setOnClickListener(new ClickListenerImpl());
        mDownloadProgressListener=new DownloadProgressListenerImpl();
    }

    private class ClickListenerImpl implements OnClickListener{
        @Override
        public void onClick(View v) {
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                String path=mUrlEditText.getText().toString();
                download(path,Environment.getExternalStorageDirectory());
            }else{
                Toast.makeText(mContext, "sderror", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void download(String path,File saveDir){
        new Thread(new DownloadRunnableImpl(path, saveDir)).start();
    }

    //下载文件的子线程
    private class DownloadRunnableImpl implements Runnable{
        private String path;
        private File SaveDir;

        public DownloadRunnableImpl(String path, File saveDir) {
            this.path = path;
            this.SaveDir = saveDir;
        }

        public void run(){
            try {
                FileDownloader fileDownloader=new FileDownloader(getApplicationContext(), path, 4, SaveDir);
                mProgressBar.setMax(fileDownloader.getFileRawSize());
                //置显示进度的回调
                fileDownloader.setDownloadProgressListener(mDownloadProgressListener);
                //开始下载
                fileDownloader.startDownload();

            } catch (Exception e) {
                Message message=new Message();
                message.what=ERROR;
                mHandler.sendMessage(message);
                e.printStackTrace();
            }

        }

    }

    private class DownloadProgressListenerImpl implements DownloadProgressListener {
        @Override
        public void onDownloadSize(int size) {
            Message message = new Message();
            message.what = NORMAL;
            message.getData().putInt(MESSAGE_KEY, size);
            mHandler.sendMessage(message);
        }
    }

    // 显示下载进度
    private class UIHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NORMAL:
                    int size = msg.getData().getInt(MESSAGE_KEY);
                    mProgressBar.setProgress(size);
                    float percentFloat = (float) (mProgressBar.getProgress() / (float) mProgressBar.getMax());
                    int percentInt = (int) (percentFloat * 100);
                    mPercentTextView.setText(percentInt + "%");
                    System.out.println("size="+size+",mProgressBar.getProgress()="+mProgressBar.getProgress()+",mProgressBar.getMax()="+mProgressBar.getMax());
                    if (mProgressBar.getProgress() == mProgressBar.getMax()) {
                        Toast.makeText(mContext,"success",Toast.LENGTH_SHORT).show();
                    }
                    break;

                case ERROR:
                    Toast.makeText(mContext, "error",Toast.LENGTH_SHORT).show();
                    break;

                default:

                    break;
            }

        }

    }

}