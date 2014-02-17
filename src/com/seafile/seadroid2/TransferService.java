package com.seafile.seadroid2;

import java.util.List;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.seafile.seadroid2.TransferManager.DownloadTaskInfo;
import com.seafile.seadroid2.TransferManager.TransferListener;
import com.seafile.seadroid2.TransferManager.UploadTaskInfo;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.SeafCachedFile;
import com.seafile.seadroid2.monitor.FileMonitorService;

public class TransferService extends Service implements TransferListener {

    @SuppressWarnings("unused")
    private static final String DEBUG_TAG = "TransferService";

    public static final String BROADCAST_ACTION =
            "com.seafile.seadroid.TX_BROADCAST";

    private final IBinder mBinder = new TransferBinder();
    private TransferManager txManager;
    private FileMonitorService mMonitorService;
    
    
    public static final String BROADCAST_FILE_DOWNLOAD_SUCCESS = "downloaded";
    public static final String BROADCAST_FILE_DOWNLOAD_FAILED = "downloadFailed";
    public static final String BROADCAST_FILE_DOWNLOAD_PROGRESS = "downloadProgress";

    public static final String BROADCAST_FILE_UPLOAD_SUCCESS = "uploaded";
    public static final String BROADCAST_FILE_UPLOAD_FAILED = "uploadFailed";
    public static final String BROADCAST_FILE_UPLOAD_PROGRESS = "uploadProgress";
    public static final String BROADCAST_FILE_UPLOAD_CANCELLED = "uploadCancelled";

    private BroadcastReceiver monitorReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            
           if (mMonitorService != null) {
               Log.d(DEBUG_TAG, "RECEIVE MONITOR");
               String path = mMonitorService.getPath();
               Log.d(DEBUG_TAG, path);
               Account account = mMonitorService.getAccount();
               Log.d(DEBUG_TAG, account.getEmail());
               DataManager dataManager = new DataManager(account);
               
               List<SeafCachedFile> cachedfiles = dataManager.getCachedFiles();
               for (SeafCachedFile cached : cachedfiles) {
                   if (path.equals(dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath())) {
                       Log.d(DEBUG_TAG, relativePathToRepo(cached.path));
                       addUploadTask(account,cached.repoID, cached.repoName, relativePathToRepo(cached.path), path, true);
                   }
               }
           }
        }
        
        private String relativePathToRepo(String filePath){
            int index = filePath.lastIndexOf("/");
            if (index == 0) {
                return "/";
            }
            return filePath.substring(0, index);
        }
        
      };
    
    @Override
    public void onCreate() {
        txManager = new TransferManager();
        txManager.setListener(this);
        
        registerReceiver(monitorReceiver, new IntentFilter(FileMonitorService.FILEMONITOR));
    }

    @Override
    public void onDestroy() {
        Log.d(DEBUG_TAG, "onDestroy");
        txManager.unsetListener();
        unbindService(mMonitorConnection);
        unregisterReceiver(monitorReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        Intent bindIntent = new Intent(this, FileMonitorService.class);
        bindService(bindIntent, mMonitorConnection, Context.BIND_AUTO_CREATE);
        
        return START_STICKY;
    }

    public class TransferBinder extends Binder {
        public TransferService getService() {
            return TransferService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        //Log.d(DEBUG_TAG, "onBind");
        return mBinder;
    }

    private ServiceConnection mMonitorConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            // TODO Auto-generated method stub
            FileMonitorService.MonitorBinder monitorBinder = (FileMonitorService.MonitorBinder)binder;
            mMonitorService = monitorBinder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // TODO Auto-generated method stub
            mMonitorService = null;
        }
        
    };
    
    
    public int addUploadTask(Account account, String repoID, String repoName, String dir,
                              String filePath, boolean isUpdate) {
        return txManager.addUploadTask(account, repoID, repoName, dir, filePath, isUpdate);
    }

    public int addDownloadTask(Account account,
                               String repoName,
                               String repoID,
                               String path) {
        return txManager.addDownloadTask(account, repoName, repoID, path);
    }

    public UploadTaskInfo getUploadTaskInfo(int taskID) {
        return txManager.getUploadTaskInfo(taskID);
    }

    public List<UploadTaskInfo> getAllUploadTaskInfos() {
        return txManager.getAllUploadTaskInfos();
    }

    public void removeUploadTask(int taskID) {
        txManager.removeUploadTask(taskID);
    }

    public void removeFinishedUploadTasks() {
        txManager.removeFinishedUploadTasks();
    }

    public void cancelUploadTask(int taskID) {
        txManager.cancelUploadTask(taskID);
    }

    public void retryUploadTask(int taskID) {
        txManager.retryUploadTask(taskID);
    }

    public DownloadTaskInfo getDownloadTaskInfo(int taskID) {
        return txManager.getDownloadTaskInfo(taskID);
    }

    @Override
    public void onFileUploadProgress(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type", BROADCAST_FILE_UPLOAD_PROGRESS)
            .putExtra("taskID", taskID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onFileUploaded(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type", BROADCAST_FILE_UPLOAD_SUCCESS)
            .putExtra("taskID", taskID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onFileUploadCancelled(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type", BROADCAST_FILE_UPLOAD_CANCELLED)
            .putExtra("taskID", taskID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onFileUploadFailed(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type", BROADCAST_FILE_UPLOAD_FAILED)
            .putExtra("taskID", taskID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onFileDownloadProgress(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type", BROADCAST_FILE_DOWNLOAD_PROGRESS)
            .putExtra("taskID", taskID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onFileDownloaded(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type", BROADCAST_FILE_DOWNLOAD_SUCCESS)
            .putExtra("taskID", taskID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onFileDownloadFailed(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type", BROADCAST_FILE_DOWNLOAD_FAILED)
            .putExtra("taskID", taskID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    public void cancelDownloadTask(int taskID) {
        txManager.cancelDownloadTask(taskID);
    }
}
