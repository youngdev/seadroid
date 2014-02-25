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
    public static final String ACCOUNTS = "com.seafile.seadroid2.monitor.accounts";
    

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
        
        ArrayList<Account> accounts = intent.getParcelableArrayListExtra(ACCOUNTS);
        addAccounts(accounts);
        
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
        
        Intent bindIntent = new Intent(this, TransferService.class);
        bindService(bindIntent, mTransferConnection, Context.BIND_AUTO_CREATE);
        
        registerReceiver(downloadReceiver, new IntentFilter(TransferService.BROADCAST_ACTION));
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        unbindService(mTransferConnection);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
    }
    
    public void addAccounts(ArrayList<Account> accounts) {
        for (int i = 0; i < accounts.size(); ++i) {
            fileMonitor.addAccount(accounts.get(i));
        }
        
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
                Log.d(LOG_TAG, rootPath + " was accessed!");
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
        private Map<Account, List<SeafileObserver>> observerMap;
        private String rootPath;
        
        public SeafileMonitor() {
            observerMap = new HashMap<Account, List<SeafileObserver>>();
        }
        
        public SeafileMonitor(String path) {
            rootPath = path;
            observerList = new ArrayList<SeafileObserver>();
        }
        
        public SeafileMonitor(Account account) {
            observerList = new ArrayList<SeafileObserver>();
            setObserversFromAccount(account);
            
        }
        
        public void setObserversFromAccount(Account account) {
            DataManager dataManager = new DataManager(account);
            List<SeafCachedFile> cachedfiles = dataManager.getCachedFiles();
            List<SeafileObserver> observerList = new ArrayList<SeafileObserver>();
            SeafileObserver observer;
            for (SeafCachedFile cached : cachedfiles) {
                File file = dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path);
                if (file.exists()) {
                    observer = new SeafileObserver(account, cached, file.getPath());
                    observer.startWatching();
                    observerList.add(observer);
                }
                
                Log.d("MonitorCachedFile", 
                    "repoDir="+dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath()+"\n"
                    +"repoName="+cached.repoName+"\n"
                    +"filePath="+cached.path+"\n"
                    +"account="+cached.getAccountSignature()
                    +"\n*********************************");
            }
            
            observerMap.put(account, observerList);
            
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
            if (observerMap.containsKey(account)) {
                return;
            }
            setObserversFromAccount(account);
        }
        
        public void removeAccount(Account account) {
            
            List<SeafileObserver> observerList = observerMap.get(account);
            
            for (int i = 0; i < observerList.size(); ++i) {
                observerList.get(i).stopWatching();
            }
            
            observerMap.remove(account);
            
        }
            
        public int size() {
            return observerMap.size();
        }
        
        public void addObserver(Account account, SeafCachedFile cachedFile, String path) {
            SeafileObserver observer = new SeafileObserver(account, cachedFile, path);
            observer.startWatching();
            observerMap.get(account).add(observer);
        }
        
    }

}
