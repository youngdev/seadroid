package com.seafile.seadroid2.monitor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.seafile.seadroid2.TransferManager;
import com.seafile.seadroid2.TransferService;
import com.seafile.seadroid2.TransferManager.DownloadTaskInfo;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.SeafCachedFile;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class FileMonitorService extends Service {

    private static final String LOG_TAG = "FileMonitorService";
    public static final String FILEMONITOR = "com.seafile.seadroid2.monitor";
    public static final String FILEPATH = "filepath";
    public static final String ACCOUNTS = "com.seafile.seadroid2.monitor.accounts";
    
    private String fileChangedPath;
    private SeafileMonitor fileMonitor;
    private Account account;
    private TransferService mTransferService;
    
    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            
            String type = intent.getStringExtra("type");
            if (type == null) {
                return;
            }
               
            int taskID = intent.getIntExtra("taskID", 0);
            DownloadTaskInfo info = mTransferService.getDownloadTaskInfo(taskID);
            if (info != null) {
                SeafCachedFile tmpCachedFile = new SeafCachedFile();
                tmpCachedFile.repoID = info.repoID;
                tmpCachedFile.repoName = info.repoName;
                tmpCachedFile.path = info.path;
                Account account = intent.getParcelableExtra("account");
                DataManager dataManager = new DataManager(account);
                Log.d("awwwwwwwwwwwww", account.email);
                String path = dataManager.getLocalRepoFile(
                        tmpCachedFile.repoName, 
                        tmpCachedFile.repoID, 
                        tmpCachedFile.path).getPath();
                if (type.equals(TransferService.BROADCAST_FILE_DOWNLOAD_SUCCESS)) {
                    fileMonitor.addObserver(account, tmpCachedFile, path);
                    fileMonitor.stopWatching();
                    fileMonitor.startWatching();
                }
            }
               
        }
        
      };
    
    
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d(LOG_TAG, "onStartCommand called.");
        
        Intent bindIntent = new Intent(this, TransferService.class);
        bindService(bindIntent, mTransferConnection, Context.BIND_AUTO_CREATE);
        
        ArrayList<Account> accounts = intent.getParcelableArrayListExtra(ACCOUNTS);
        fileMonitor = new SeafileMonitor(accounts);
        fileMonitor.startWatching();
        
        return START_STICKY;
        
    }
    
    
    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");
        registerReceiver(downloadReceiver, new IntentFilter(TransferService.BROADCAST_ACTION));
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        unbindService(mTransferConnection);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
    }
    
    
    private ServiceConnection mTransferConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            // TODO Auto-generated method stub
            TransferService.TransferBinder transferBinder = (TransferService.TransferBinder)binder;
            mTransferService = transferBinder.getService();
            LocalBroadcastManager.getInstance(FileMonitorService.this).registerReceiver(downloadReceiver, new IntentFilter(TransferService.BROADCAST_ACTION));
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // TODO Auto-generated method stub
            mTransferService = null;
        }
        
    };
    
    public void setPath(String path) {
        fileChangedPath = path;
    }
    
    public String getPath() {
        return fileChangedPath;
    }
    
    public void setAccount(Account account) {
        this.account = account;
    }
    
    public Account getAccount() {
        return account;
    }
    
    public class SeafileObserver extends FileObserver {

        private final String LOG_TAG = "FILE_MONITOR";
        private String rootPath;
        private Account account;
        private SeafCachedFile cachedFile;
        
        public SeafileObserver(Account account, SeafCachedFile cachedFile, String path) {
            super(path, FileObserver.ALL_EVENTS);
            this.account = account;
            this.cachedFile = cachedFile;
            rootPath = path;
        }

        public String getPath() {
            return rootPath;
        }
        
        private String relativePathToRepo(String filePath){
            int index = filePath.lastIndexOf("/");
            if (index == 0) {
                return "/";
            }
            return filePath.substring(0, index);
        }
        
        @Override
        public void onEvent(int event, String path) {
            // TODO Auto-generated method stub
            switch (event) {
            case FileObserver.ACCESS:
                Log.d(LOG_TAG, path + " was accessed!");
                break;
            case FileObserver.MODIFY:
                Log.d(LOG_TAG, rootPath + " was modified!");
                if (mTransferService != null) {
                    mTransferService.addUploadTask(account,cachedFile.repoID, cachedFile.repoName, relativePathToRepo(cachedFile.path), rootPath, true);
                }       
                break;
            default:
                break;
            
            }
        }
        
        @Override
        protected void finalize() {
            Log.d(LOG_TAG, "The "+rootPath+" Observer is collected.");
        }

    }
    
    public class SeafileMonitor {
        
        private ArrayList<SeafileObserver> observerList;
        private String rootPath;
        private ArrayList<String> paths;
        
        public SeafileMonitor(String path) {
            rootPath = path;
            observerList = new ArrayList<SeafileObserver>();
        }
        
        public SeafileMonitor(Account account) {
            observerList = new ArrayList<SeafileObserver>();
            setObserversFromAccount(account);
            
        }
        
//        public SeafileMonitor(ArrayList<String> paths) {
//            this.paths = new ArrayList<String>(paths);
//            observerList = new ArrayList<SeafileObserver>();
//        }

        public SeafileMonitor(ArrayList<Account> accounts) {
            
            observerList = new ArrayList<SeafileObserver>();
            
            for (int i = 0; i < accounts.size(); ++i) {
                setObserversFromAccount(accounts.get(i));
            }
            
        }
        
        public void setObserversFromAccount(Account account) {
            DataManager dataManager = new DataManager(account);
            List<SeafCachedFile> cachedfiles = dataManager.getCachedFiles();
            //ArrayList<String> paths = new ArrayList<String>();
            String path;
            for (SeafCachedFile cached : cachedfiles) {
                path = dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath();
                
                observerList.add(new SeafileObserver(account, cached, path));
                
                Log.d("MonitorCachedFile", 
                    "repoDir="+dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath()+"\n"
                    +"repoName="+cached.repoName+"\n"
                    +"filePath="+cached.path+"\n"
                    +"account="+cached.getAccountSignature()
                    +"\n*********************************");
            }
            
        }
           
//        public void init() {
//            for (int i = 0; i < paths.size(); ++i) {
//                observerList.add(new SeafileObserver(paths.get(i)));
//            }
//        }
        
//        private void recursiveDirectory(String path) {
//            
//            File[] files = new File(path).listFiles();
//            int fileNumber = files.length;
//            for (int i = 0; i < fileNumber; ++i) {
//                if (files[i].isDirectory()) {
//                    observerList.add(new SeafileObserver(files[i].getPath()));
//                    recursiveDirectory(files[i].getPath());
//                } else {
//                    continue;
//                }
//            }
//            
//        }

        
        public void addObserver(Account account, SeafCachedFile cachedFile, String path) {
            observerList.add(new SeafileObserver(account, cachedFile, path));
        }
        
        
        public void removeObserver(String path) {
            for (int i = 0; i < observerList.size(); ++i) {
                if (path.equals(observerList.get(i).getPath())) {
                    observerList.remove(i);
                }
            }
        }
        
        public void startWatching() {
            for (int i = 0; i < observerList.size(); ++i) {          
                observerList.get(i).startWatching();
            }
            
        }
        
        public void stopWatching() {
            for (int i = 0; i < observerList.size(); ++i) {            
                observerList.get(i).stopWatching();
            }
            
        }
        
    }

}
