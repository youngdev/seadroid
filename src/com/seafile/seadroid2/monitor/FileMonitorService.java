package com.seafile.seadroid2.monitor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.seafile.seadroid2.TransferManager;
import com.seafile.seadroid2.TransferService;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.SeafCachedFile;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.util.Log;

public class FileMonitorService extends Service {

    private static final String LOG_TAG = "FileMonitorService";
    public static final String FILEMONITOR = "com.seafile.seadroid2.monitor";
    public static final String FILEPATH = "filepath";
    public static final String ACCOUNTS = "com.seafile.seadroid2.monitor.accounts";
    public static final String DOWNLOADED = "com.seafile.seadroid2.monitor.downloaded";
    public static final String DOWNLOADED_PATH = "com.seafile.seadroid2.monitor.downloadedpath";
    public static final String DOWNLOADED_ACCOUNT = "com.seafile.seadroid2.monitor.downloadedaccount";
    
    private final IBinder mBinder = new MonitorBinder();
    private String fileChangedPath;
    private SeafileMonitor fileMonitor;
    private Account account;
    
    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            
           if (intent != null) {
               Account account = intent.getParcelableExtra(DOWNLOADED_ACCOUNT);
               String path = intent.getStringExtra(DOWNLOADED_PATH);
               Log.d("DownloadedPath", path);
               fileMonitor.addObserver(account, path);
               fileMonitor.stopWatching();
               fileMonitor.startWatching();
           }
        }
        
      };
    
    
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d(LOG_TAG, "onStartCommand called.");
        ArrayList<Account> accounts = intent.getParcelableArrayListExtra(ACCOUNTS);
        fileMonitor = new SeafileMonitor(accounts);
        fileMonitor.startWatching();
        return START_STICKY;
        
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        Log.d(LOG_TAG, "Bind Service!");
        return mBinder;
    }
    
    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");
        registerReceiver(downloadReceiver, new IntentFilter(FileMonitorService.DOWNLOADED));
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        unregisterReceiver(downloadReceiver);
    }
    
    public class MonitorBinder extends Binder {
        public FileMonitorService getService() {
            return FileMonitorService.this;
        }
        
    }
    
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
        
        public SeafileObserver(Account account, String path) {
            super(path, FileObserver.ALL_EVENTS);
            this.account = account;
            rootPath = path;
        }

        public String getPath() {
            return rootPath;
        }
        
        @Override
        public void onEvent(int event, String path) {
            // TODO Auto-generated method stub
            switch (event) {
            case FileObserver.ACCESS:
                Log.d(LOG_TAG, path + " was accessed!");
                break;
            case FileObserver.MODIFY:
                Intent intent = new Intent(FileMonitorService.FILEMONITOR);
                Log.d(LOG_TAG, rootPath + " was modified!");
                setPath(rootPath);
                setAccount(account);
                sendBroadcast(intent);         
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
            ArrayList<String> paths = new ArrayList<String>();
            String path;
            for (SeafCachedFile cached : cachedfiles) {
                path = dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath();
                //this.paths.add(path);
                paths.add(path);
                Log.d("MonitorCachedFile", 
                    "repoDir="+dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath()+"\n"
                    +"repoName="+cached.repoName+"\n"
                    +"filePath="+cached.path+"\n"
                    +"account="+cached.getAccountSignature()
                    +"\n*********************************");
            }
            for (int i = 0; i < paths.size(); ++i) {
                observerList.add(new SeafileObserver(account, paths.get(i)));
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
        
        
        public void addObserver(Account account, String path) {
            observerList.add(new SeafileObserver(account, path));
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
