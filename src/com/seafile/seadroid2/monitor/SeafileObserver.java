package com.seafile.seadroid2.monitor;

import android.content.Intent;
import android.os.FileObserver;
import android.util.Log;

public class SeafileObserver extends FileObserver {

    private final String LOG_TAG = "FILE_MONITOR";
    private String rootPath;
    FileMonitorService monitorService;
    
    public SeafileObserver(FileMonitorService service, String path) {
        super(path, FileObserver.ALL_EVENTS);
        rootPath = path;
        monitorService = service;
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
            //intent.putExtra(FileMonitorService.FILEPATH, rootPath);
            Log.d(LOG_TAG, path + " was modified!");
            monitorService.setPath(rootPath);
            monitorService.sendBroadcast(intent);             
            break;
        default:
            break;
        
        }
    }

}
