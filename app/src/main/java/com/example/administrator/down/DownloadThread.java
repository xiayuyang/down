package com.example.administrator.down;

/**
 * Created by Administrator on 2018/6/9.
 */

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadThread extends Thread{
    private FileDownloader fileDownloader;
    private int threadId;
    private int everyThreadNeedDownloadLength;
    private int downLength;
    private URL downUrl;
    private File localFile;
    private Boolean isFinish=false;


    public DownloadThread(FileDownloader fileDownloader, int threadId, int everyThreadNeedDownloadLength,int downLength, URL downUrl, File localFile) {
        this.fileDownloader = fileDownloader;
        this.threadId = threadId;
        this.everyThreadNeedDownloadLength = everyThreadNeedDownloadLength;
        this.downLength = downLength;
        this.downUrl = downUrl;
        this.localFile = localFile;
    }


    @Override
    public void run() {
        try {
            //当此线程已下载量小于应下载量
            if(downLength<everyThreadNeedDownloadLength){
                HttpURLConnection httpURLConnection=(HttpURLConnection) downUrl.openConnection();
                httpURLConnection.setConnectTimeout(5*1000);
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.setRequestProperty("Accept", "image/gif, image/jpeg, image/pjpeg, image/pjpeg, application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, */*");
                httpURLConnection.setRequestProperty("Accept-Language", "zh-CN");
                httpURLConnection.setRequestProperty("Referer", downUrl.toString());
                httpURLConnection.setRequestProperty("Charset", "UTF-8");

                //开始下载位置
                int startPosition=everyThreadNeedDownloadLength*(threadId-1)+downLength;
                //结束下载位置
                int endPosition=everyThreadNeedDownloadLength*threadId-1;
                //处理最后一条下载线程的特殊情况
                if (endPosition>fileDownloader.getFileRawSize()) {
                    int redundant=endPosition-(fileDownloader.getFileRawSize()-1);
                    endPosition=fileDownloader.getFileRawSize()-1;
                    everyThreadNeedDownloadLength=everyThreadNeedDownloadLength-redundant;
                }
                //设置下载的起止位置
                httpURLConnection.setRequestProperty("Range","bytes="+startPosition+"-"+endPosition);
                System.out.println("====> 每条线程的下载起始情况 threadId="+threadId+",startPosition="+startPosition+",endPosition="+endPosition);
                httpURLConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.2; Trident/4.0; .NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)");
                httpURLConnection.setRequestProperty("Connection", "Keep-Alive");

                RandomAccessFile randomAccessFile=new RandomAccessFile(localFile, "rwd");
                randomAccessFile.seek(startPosition);
                InputStream inputStream=httpURLConnection.getInputStream();
                int len=0;
                byte [] b=new byte[1024];
                while((len=inputStream.read(b))!=-1 && downLength<everyThreadNeedDownloadLength){
                    downLength=downLength+len;
                    int redundant =0;
                    //处理每条线程最后一次读取可能会多读数据的问题
                    if (downLength>everyThreadNeedDownloadLength) {
                        redundant =downLength-everyThreadNeedDownloadLength;
                        downLength=everyThreadNeedDownloadLength;
                    }
                    randomAccessFile.write(b, 0, len-redundant);

                    //实时更新该线程的已下载量,从而刷新每条线程的已下载量
                    fileDownloader.updateEveryThreadDownloadLength(threadId, downLength);
                    //实时更新已经下载的总量
                    fileDownloader.appendDownloadTotalSize(len-redundant);
                }
                inputStream.close();
                randomAccessFile.close();
                //改变标志位
                isFinish=true;
            }

        } catch (Exception e) {
            downLength=-1;
            e.printStackTrace();
        }

    }

    //判断是否已经下载完成
    public Boolean isFinish(){
        return isFinish;
    }

    //返回该线程已经下载的数据量,若-1则代表失败
    public int getDownloadSize(){
        return downLength;
    }

}