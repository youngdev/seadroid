package com.seafile.seadroid2.monitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import com.seafile.seadroid2.data.DataManager;

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
    private final IBinder mBinder = new MonitorBinder();
    private String fileChangedPath;
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d(LOG_TAG, "onStartCommand called.");
//        ArrayList<String> paths = intent.getStringArrayListExtra(FILEPATH);
//        SeafileMonitor monitor = new SeafileMonitor(paths);
//        monitor.initt();
        Log.d(LOG_TAG, DataManager.getExternalRootDirectory());
//        SeafileMonitor monitor = new SeafileMonitor(DataManager.getExternalRootDirectory());
//        monitor.init();
//        monitor.startWatching();
        RecursiveFileObserver observer = new RecursiveFileObserver(DataManager.getExternalRootDirectory());
        observer.startWatching();
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
    
//    public class SeafileObserver extends FileObserver {
//
//        private final String LOG_TAG = "FILE_MONITOR";
//        private String rootPath;
//        
//        public SeafileObserver(String path) {
//            super(path, FileObserver.ALL_EVENTS);
//            rootPath = path;
//        }
//
//        public String getPath() {
//            return rootPath;
//        }
//        
//        @Override
//        public void onEvent(int event, String path) {
//            // TODO Auto-generated method stub
//            switch (event) {
//            case FileObserver.ACCESS:
//                Log.d(LOG_TAG, path + " was accessed!");
//                break;
//            case FileObserver.MODIFY:
//                Intent intent = new Intent(FileMonitorService.FILEMONITOR);
//                //intent.putExtra(FileMonitorService.FILEPATH, rootPath);
//                Log.d(LOG_TAG, path + " was modified!");
//                FileMonitorService.this.setPath(rootPath);
//                sendBroadcast(intent);             
//                break;
//            default:
//                break;
//            
//            }
//        }
//        
//        @Override
//        protected void finalize() {
//            Log.d(LOG_TAG, "The "+rootPath+" Observer is collected.");
//        }
//
//    }
//    
//    public class SeafileMonitor {
//        
//        private ArrayList<SeafileObserver> observerList;
//        private String rootPath;
//        private ArrayList<String> paths;
//        
//        public SeafileMonitor(String path) {
//            rootPath = path;
//            observerList = new ArrayList<SeafileObserver>();
//        }
//        
//        public SeafileMonitor(ArrayList<String> paths) {
//            this.paths = new ArrayList<String>(paths);
//            observerList = new ArrayList<SeafileObserver>();
//        }
//
//        public void init() {
//            recursiveDirectory(rootPath);      
//        }
//           
//        public void initt() {
//            for (int i = 0; i < paths.size(); ++i) {
//                observerList.add(new SeafileObserver(paths.get(i)));
//            }
//        }
//        
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
//        
//        
//        public void startWatching() {
//            for (int i = 0; i < observerList.size(); ++i) {          
//                observerList.get(i).startWatching();
//            }
//            
//        }
//        
//        public void stopWatching() {
//            for (int i = 0; i < observerList.size(); ++i) {            
//                observerList.get(i).stopWatching();
//            }
//            
//        }
//        
//    }

    public class RecursiveFileObserver extends FileObserver {

        List<SingleFileObserver> mObservers;
        String mPath;
        int mMask;

        public RecursiveFileObserver(String path) {
            this(path, ALL_EVENTS);
        }

        public RecursiveFileObserver(String path, int mask) {
            super(path, mask);
            mPath = path;
            mMask = mask;
        }

        @Override
        public void startWatching() {
            if (mObservers != null) return;
            mObservers = new ArrayList<SingleFileObserver>();
            Stack<String> stack = new Stack<String>();
            stack.push(mPath);

            while (!stack.empty()) {
                String parent = stack.pop();
                mObservers.add(new SingleFileObserver(parent, mMask));
                File path = new File(parent);
                File[] files = path.listFiles();
                if (files == null) continue;
                for (int i = 0; i < files.length; ++i) {
                    if (files[i].isDirectory() && !files[i].getName().equals(".")
                        && !files[i].getName().equals("..")) {
                        stack.push(files[i].getPath());
                    }
                }
            }
            for (int i = 0; i < mObservers.size(); i++)
                mObservers.get(i).startWatching();
        }

        @Override
        public void stopWatching() {
            if (mObservers == null) return;

            for (int i = 0; i < mObservers.size(); ++i)
                mObservers.get(i).stopWatching();

            mObservers.clear();
            mObservers = null;
        }

        @Override
        public void onEvent(int event, String path) {
          switch (event) {
          case FileObserver.ACCESS:
              Log.d(LOG_TAG, path + " was accessed!");
              break;
          case FileObserver.MODIFY:
              Intent intent = new Intent(FileMonitorService.FILEMONITOR);
              //intent.putExtra(FileMonitorService.FILEPATH, rootPath);
              Log.d(LOG_TAG, path + " was modified!");
              setPath(path);
              sendBroadcast(intent);             
              break;
          default:
              break;
          }
       }

        private class SingleFileObserver extends FileObserver {
            private String mPath;

            public SingleFileObserver(String path, int mask) {
                super(path, mask);
                mPath = path;
            }

            @Override
            public void onEvent(int event, String path) {
                String newPath = mPath + "/" + path;
                RecursiveFileObserver.this.onEvent(event, newPath);
            } 

            @Override
            protected void finalize() {
                Log.d(LOG_TAG, mPath + " was collected");
            }
            
        }
    }
}
