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
import com.seafile.seadroid2.TransferService.TransferBinder;
import com.seafile.seadroid2.Utils;
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
    public static final String ACCOUNT = "com.seafile.seadroid2.monitor.account";
    

    private SeafileMonitor fileMonitor = new SeafileMonitor();
    private TransferService mTransferService;
    private final IBinder mBinder = new MonitorBinder();
    
    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            
            String type = intent.getStringExtra("type");
            if (type == null) {
                return;
            }
            
            if (type.equals(TransferService.BROADCAST_FILE_DOWNLOAD_SUCCESS)) {
                
                int taskID = intent.getIntExtra("taskID", 0);
                DownloadTaskInfo info = mTransferService.getDownloadTaskInfo(taskID);
                if (info != null) {
                    SeafCachedFile tmpCachedFile = new SeafCachedFile();
                    tmpCachedFile.repoID = info.repoID;
                    tmpCachedFile.repoName = info.repoName;
                    tmpCachedFile.path = info.path;
                    Account account = info.account;
                    DataManager dataManager = new DataManager(account);
                    Log.d(LOG_TAG, account.email);
                    String path = dataManager.getLocalRepoFile(
                            tmpCachedFile.repoName, 
                            tmpCachedFile.repoID, 
                            tmpCachedFile.path).getPath();
                    fileMonitor.addObserver(account, tmpCachedFile, path);
                }
            }
                      
        }
        
      };
    
    
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d(LOG_TAG, "onStartCommand called.");
        
        Intent bindIntent = new Intent(this, TransferService.class);
        bindService(bindIntent, mTransferConnection, Context.BIND_AUTO_CREATE);
            
        Account account = intent.getParcelableExtra(ACCOUNT);
        addAccount(account);
        
        return START_STICKY;
        
    }
    
    
    public class MonitorBinder extends Binder {
        public FileMonitorService getService() {
            return FileMonitorService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");
        return mBinder;
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
    
    public void addAccount(Account account) {
        Log.d(LOG_TAG, account.email);
        fileMonitor.addAccount(account);
    }
    
    public void removeAccount(Account account) {
        Log.d(LOG_TAG, account.email);
        fileMonitor.removeAccount(account);
    }
    
    private ServiceConnection mTransferConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            TransferService.TransferBinder transferBinder = (TransferService.TransferBinder)binder;
            mTransferService = transferBinder.getService();
            LocalBroadcastManager.getInstance(FileMonitorService.this).registerReceiver(downloadReceiver, new IntentFilter(TransferService.BROADCAST_ACTION));
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mTransferService = null;
        }
        
    };
    
    
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
        
        public void setAccount(Account account) {
            this.account = account;
        }
        
        public Account getAccount() {
            return account;
        }
        
        @Override
        public void onEvent(int event, String path) {
            switch (event) {
            case FileObserver.ACCESS:
                Log.d(LOG_TAG, path + " was accessed!");
                break;
            case FileObserver.MODIFY:
                Log.d(LOG_TAG, rootPath + " was modified!");
//                if (mTransferService != null) {
//                    mTransferService.addUploadTask(account,cachedFile.repoID, cachedFile.repoName, Utils.getParentPath(cachedFile.path), rootPath, true);
//                }       
                break;
            case FileObserver.CLOSE_WRITE:
                Log.d(LOG_TAG, rootPath + " was accessed for writing!");
                if (mTransferService != null) {
                    mTransferService.addUploadTask(account,cachedFile.repoID, cachedFile.repoName, Utils.getParentPath(cachedFile.path), rootPath, true);
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
        private ArrayList<Account> accounts;
        private String rootPath;
        private ArrayList<String> paths;
        private boolean watching = false;
        
        public SeafileMonitor() {
            observerList = new ArrayList<SeafileObserver>();
            accounts = new ArrayList<Account>();
        }
        
        public SeafileMonitor(String path) {
            rootPath = path;
            observerList = new ArrayList<SeafileObserver>();
        }
        
        public SeafileMonitor(Account account) {
            observerList = new ArrayList<SeafileObserver>();
            setObserversFromAccount(account);
            
        }
        

        public SeafileMonitor(ArrayList<Account> accounts) {
            
            observerList = new ArrayList<SeafileObserver>();
            
            for (int i = 0; i < accounts.size(); ++i) {
                setObserversFromAccount(accounts.get(i));
            }
            
        }
        
        public void setObserversFromAccount(Account account) {
            DataManager dataManager = new DataManager(account);
            List<SeafCachedFile> cachedfiles = dataManager.getCachedFiles();
            String path;
            for (SeafCachedFile cached : cachedfiles) {
                File file = dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path);
                if (file.exists()) {
                    observerList.add(new SeafileObserver(account, cached, file.getPath()));
                }
                
                Log.d("MonitorCachedFile", 
                    "repoDir="+dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath()+"\n"
                    +"repoName="+cached.repoName+"\n"
                    +"filePath="+cached.path+"\n"
                    +"account="+cached.getAccountSignature()
                    +"\n*********************************");
            }
            
        }
        
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

        
        public void addAccount(Account account) {
            if (accounts.contains(account)) {
                return;
            }
            setObserversFromAccount(account);
            accounts.add(account);
            startWatching();     
        }
        
        public void removeAccount(Account account) {
            for (int i = 0; i < size(); ++i) {
                if (account.equals(observerList.get(i).getAccount())) {
                    observerList.remove(i);
                }
            }
            
            int index = accounts.indexOf(account);
            if (index != -1) {
                accounts.remove(accounts.indexOf(account));
            }
            
            startWatching();
        }
        
        private void setWatching(boolean watching) {
            this.watching = watching;
        }
        
        private boolean isWatching() {
            return watching;
        }
        
        public int size() {
            return observerList.size();
        }
        
        public void addObserver(Account account, SeafCachedFile cachedFile, String path) {
            observerList.add(new SeafileObserver(account, cachedFile, path));
            startWatching();
        }
        
        
        public void removeObserver(String path) {
            for (int i = 0; i < observerList.size(); ++i) {
                if (path.equals(observerList.get(i).getPath())) {
                    observerList.remove(i);
                }
            }
            startWatching();
        }
        
        public void startWatching() {
            
            if (isWatching()) {
                stopWatching();
            }
            for (int i = 0; i < observerList.size(); ++i) {          
                observerList.get(i).startWatching();
            }
            setWatching(true);
        }
        
        public void stopWatching() {
            
            if (!isWatching()) {
                return;
            } 
            for (int i = 0; i < observerList.size(); ++i) {            
                observerList.get(i).stopWatching();
            }
            setWatching(false);
        }
        
    }

}
