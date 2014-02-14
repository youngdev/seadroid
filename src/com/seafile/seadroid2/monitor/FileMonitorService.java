package com.seafile.seadroid2.monitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import com.seafile.seadroid2.TransferService;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.SeafCachedFile;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.util.Log;

public class FileMonitorService extends Service {

    private static final String LOG_TAG = "FileMonitorService";
    public static final String FILEMONITOR = "com.seafile.seadroid2.monitor";
    public static final String FILEPATH = "filepath";
    public static final String CURRENT_ACCOUNT = "com.seafile.seadroid2.monitor.account";
    
    private final IBinder mBinder = new MonitorBinder();
    private String fileChangedPath;
    private SeafileMonitor fileMonitor;
    private Account account;
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d(LOG_TAG, "onStartCommand called.");
        account = intent.getParcelableExtra(CURRENT_ACCOUNT);
        fileMonitor = new SeafileMonitor(account);
        fileMonitor.init();
        fileMonitor.startWatching();
        return START_STICKY;
        
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        Log.d(LOG_TAG, "Bind Service!");
        return mBinder;
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
    
    public Account getAccount() {
        return account;
    }
    
    public class SeafileObserver extends FileObserver {

        private final String LOG_TAG = "FILE_MONITOR";
        private String rootPath;
        
        public SeafileObserver(String path) {
            super(path, FileObserver.ALL_EVENTS);
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
            DataManager dataManager = new DataManager(account);
            List<SeafCachedFile> cachedfiles = dataManager.getCachedFiles();
            paths = new ArrayList<String>();
            String path;
            for (SeafCachedFile cached : cachedfiles) {
                path = dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath();
                paths.add(path);
                Log.d("MonitorCachedFile", 
                    "repoDir="+dataManager.getLocalRepoFile(cached.repoName, cached.repoID, cached.path).getPath()+"\n"
                    +"repoName="+cached.repoName+"\n"
                    +"filePath="+cached.path+"\n"
                    +"account="+cached.getAccountSignature()
                    +"\n*********************************");
            }
            observerList = new ArrayList<SeafileObserver>();
        }
        
        public SeafileMonitor(ArrayList<String> paths) {
            this.paths = new ArrayList<String>(paths);
            observerList = new ArrayList<SeafileObserver>();
        }

//        public void init() {
//            recursiveDirectory(rootPath);      
//        }
           
        public void init() {
            for (int i = 0; i < paths.size(); ++i) {
                observerList.add(new SeafileObserver(paths.get(i)));
            }
        }
        
        private void recursiveDirectory(String path) {
            
            File[] files = new File(path).listFiles();
            int fileNumber = files.length;
            for (int i = 0; i < fileNumber; ++i) {
                if (files[i].isDirectory()) {
                    observerList.add(new SeafileObserver(files[i].getPath()));
                    recursiveDirectory(files[i].getPath());
                } else {
                    continue;
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
