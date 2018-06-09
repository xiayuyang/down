package com.example.administrator.down;

/**
 * Created by Administrator on 2018/6/9.
 */


import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;


public class FileDownloader {
    private Context mContext;
    //下载路径
    private String mDownloadPath;
    //待下载文件的原始长度
    private int rawFileSize=0;
    //保存下载文件的本地文件
    private File mLocalFile;
    //已下载大小
    private int downloadTotalSize=0;
    //下载此文件需要的各个线程
    private DownloadThread [] downloadThreadsArray;
    //每条线程需下载的长度
    private int everyThreadNeedDownloadLength;
    //存放目前每条线程的信息包含其id和已下载大小
    private Map<Integer,Integer> mCurrentEveryThreadInfoMap;
    //用于对各个线程进行操作
    private DownloadThreadHelper mDownloadThreadHelper;

    private DownloadProgressListener mDownloadProgressListener;

    public FileDownloader(Context context,String downloadPath,int threadNum,File fileSaveDir){
        System.out.println("源文件路径 downloadPath= "+downloadPath);
        System.out.println("下载开启的线程数 threadNum="+threadNum);
        try {
            mContext=context;
            mDownloadPath=downloadPath;
            mCurrentEveryThreadInfoMap=new HashMap<Integer,Integer>();
            mDownloadThreadHelper=new DownloadThreadHelper(context);
            downloadThreadsArray=new DownloadThread[threadNum];

            URL downloadUrl=new URL(downloadPath);
            HttpURLConnection httpURLConnection=(HttpURLConnection) downloadUrl.openConnection();
            httpURLConnection.setReadTimeout(5*1000);
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setRequestProperty("Accept", "image/gif, image/jpeg, image/pjpeg, image/pjpeg, application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, */*");
            httpURLConnection.setRequestProperty("Accept-Language", "zh-CN");
            httpURLConnection.setRequestProperty("Referer", downloadPath);
            httpURLConnection.setRequestProperty("Charset", "UTF-8");
            httpURLConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.2; Trident/4.0; .NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)");
            httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
            httpURLConnection.connect();
            if(httpURLConnection.getResponseCode()==200){
                //第一步:获得源文件大小
                rawFileSize=httpURLConnection.getContentLength();
                System.out.println("源文件大小 rawFileSize="+rawFileSize);

                //第二步:计算每条线程需要下载的数据长度
                everyThreadNeedDownloadLength=rawFileSize%threadNum==0 ? rawFileSize/threadNum : rawFileSize/threadNum+1;
                System.out.println("每条线程应下载大小 everyThreadNeedDownloadLength="+everyThreadNeedDownloadLength);
                if(rawFileSize<=0){
                    throw new RuntimeException("file is not found");
                }


                //第三步:建立本地文件并设置本地RandomAccessFile的大小
                String rawFileName=getFileName(httpURLConnection);
                if(!fileSaveDir.exists()){
                    fileSaveDir.mkdirs();
                }
                mLocalFile=new File(fileSaveDir, rawFileName);
                System.out.println("本地文件路径 mLocalFile.getAbsolutePath()="+mLocalFile.getAbsolutePath());

                RandomAccessFile randomAccessFile=new RandomAccessFile(mLocalFile, "rw");
                if(rawFileSize>0){
                    randomAccessFile.setLength(rawFileSize);
                }
                randomAccessFile.close();

                //第四步:取出每条线程的上次下载的情况和已下载的总长度
                /**
                 * 以下操作围绕断点进行的:
                 * 1 从数据库取出每条线程上一次的下载情况,存入everyThreadLastDownloadLengthMap
                 * 2 判断上次下载时开启的线程数是否和本次下载开启线程数一致
                 *   若不一致则无法在原基础上继续断点下载,则将mCurrentEveryThreadInfoMap中各条线程下载量设置为0
                 *   若一致则取出已下载的数据总量
                 */

                //若以前下载过,则取出每条线程以前的情况存入mCurrentEveryThreadInfoMap
                Map<Integer,Integer> everyThreadLastDownloadLengthMap=mDownloadThreadHelper.getEveryThreadDownloadLength(downloadPath);
                if(everyThreadLastDownloadLengthMap.size()>0){
                    for(Map.Entry<Integer, Integer> entry:everyThreadLastDownloadLengthMap.entrySet()){
                        mCurrentEveryThreadInfoMap.put(entry.getKey(), entry.getValue());
                        System.out.println("--> 断点回复处 --> threadID="+entry.getKey()+",已下载数据量 length="+entry.getValue());
                    }
                }

                //若以往的线程条数和现在的线程条数不一致,则无法按照
                //断点继续下载.所以将每条已下载的数据量置为0,并更新数据库
                //若以往的线程条数和现在的线程条数一致,则取出已下载的数据总量
                if(downloadThreadsArray.length!=mCurrentEveryThreadInfoMap.size()){
                    mCurrentEveryThreadInfoMap.clear();
                    for(int i=1;i<=downloadThreadsArray.length;i++){
                        mCurrentEveryThreadInfoMap.put(i, 0);
                    }
                    mDownloadThreadHelper.saveEveryThreadDownloadLength(mDownloadPath, mCurrentEveryThreadInfoMap);
                }else{
                    for(int i=1;i<=threadNum;i++){
                        downloadTotalSize=downloadTotalSize+mCurrentEveryThreadInfoMap.get(i);
                    }
                    // 更新已经下载的数据量
                    if (mDownloadProgressListener != null) {
                        mDownloadProgressListener.onDownloadSize(downloadTotalSize);
                    }
                    System.out.println("--> 断点回复处 --> 已经下载  downloadTotalSize="+downloadTotalSize);
                }
            }else{
                throw new RuntimeException("The HttpURLConnection is fail");
            }

        } catch (Exception e) {
            throw new RuntimeException("Init FileDownloader is fail");
        }
    }


    public int startDownload( ){
        try {

            URL downloadURL=new URL(mDownloadPath);

            /**
             * 对每条线程的下载情况进行判断
             * 如果没有下载完,则继续下载
             * 否则将该线程置为空
             */
            for(int i=1;i<=downloadThreadsArray.length;i++){
                //取出此线程已经下载的大小
                int existDownloadSize=mCurrentEveryThreadInfoMap.get(i);
                if(existDownloadSize<everyThreadNeedDownloadLength && downloadTotalSize<rawFileSize){
                    downloadThreadsArray[i-1]=new DownloadThread(this, i, everyThreadNeedDownloadLength, mCurrentEveryThreadInfoMap.get(i), downloadURL, mLocalFile);
                    //设置优先级
                    downloadThreadsArray[i-1].setPriority(7);
                    //开始下载,注意数组的开始角标是从零开始的
                    downloadThreadsArray[i-1].start();
                }else{
                    downloadThreadsArray[i-1]=null;
                }
            }


            /**
             * 注意:
             * 对于下载失败的线程(-1)重新开始下载
             */
            Boolean isAllFinish=true;
            while (isAllFinish) {
                isAllFinish = false;
                for (int i = 1; i <= downloadThreadsArray.length; i++) {
                    if (downloadThreadsArray[i - 1] != null && !downloadThreadsArray[i - 1].isFinish()) {
                        isAllFinish = true;
                        if (downloadThreadsArray[i - 1].getDownloadSize() == -1) {
                            downloadThreadsArray[i - 1] = new DownloadThread(this, i, everyThreadNeedDownloadLength,mCurrentEveryThreadInfoMap.get(i), downloadURL,mLocalFile);
                            downloadThreadsArray[i - 1].setPriority(7);
                            downloadThreadsArray[i - 1].start();
                        }

                    }
                }
            }

            //下载完成,删除记录
            mDownloadThreadHelper.deleteEveryThreadDownloadRecord(mDownloadPath);

        } catch (Exception e) {
            throw new RuntimeException("the download is fail");
        }
        return downloadTotalSize;
    }

    //获取线程数
    public int getThreadsNum(){
        return downloadThreadsArray.length;
    }

    //获取原始文件大小
    public int getFileRawSize(){
        return rawFileSize;
    }

    //更新已经下载的总数据量
    protected synchronized void appendDownloadTotalSize(int newSize){
        downloadTotalSize=downloadTotalSize+newSize;
        if (mDownloadProgressListener!=null) {
            mDownloadProgressListener.onDownloadSize(downloadTotalSize);
        }
        System.out.println("当前总下载量 downloadTotalSize="+downloadTotalSize);
    }

    //更新每条线程已经下载的数据量
    protected synchronized void updateEveryThreadDownloadLength(int threadid,int position){
        mCurrentEveryThreadInfoMap.put(threadid, position);
        mDownloadThreadHelper.updateEveryThreadDownloadLength(mDownloadPath, mCurrentEveryThreadInfoMap);
    }


    //获取文件名
    public String getFileName(HttpURLConnection conn){
        String filename = mDownloadPath.substring(mDownloadPath.lastIndexOf('/') + 1);
        if(filename==null || "".equals(filename.trim())){
            for (int i = 0;; i++) {
                String mine = conn.getHeaderField(i);
                if (mine == null) break;
                if("content-disposition".equals(conn.getHeaderFieldKey(i).toLowerCase())){
                    Matcher m = Pattern.compile(".*filename=(.*)").matcher(mine.toLowerCase());
                    if(m.find()) return m.group(1);
                }
            }
            filename = UUID.randomUUID()+ ".tmp";
        }
        return filename;
    }

    public void setDownloadProgressListener(DownloadProgressListener downloadProgressListener){
        mDownloadProgressListener=downloadProgressListener;
    }
}