package com.seafile.seadroid2.monitor;

import java.io.File;
import java.util.ArrayList;

import android.util.Log;

public class SeafileMonitor {
    
    private ArrayList<SeafileObserver> observerList;
    private String rootPath;
    private ArrayList<String> paths;
    private FileMonitorService monitorService;
    
    public SeafileMonitor(FileMonitorService service, String path) {
        monitorService = service;
        rootPath = path;
        observerList = new ArrayList<SeafileObserver>();
    }
    
    public SeafileMonitor(FileMonitorService service, ArrayList<String> paths) {
        monitorService = service;
        this.paths = new ArrayList<String>(paths);
        observerList = new ArrayList<SeafileObserver>();
    }

    public void init() {
        recursiveDirectory(rootPath);  
    }
       
    public void initt() {
        for (int i = 0; i < paths.size(); ++i) {
            observerList.add(new SeafileObserver(monitorService, paths.get(i)));
        }
    }
    
    private void recursiveDirectory(String path) {
        
        File[] files = new File(path).listFiles();
        int fileNumber = files.length;
        for (int i = 0; i < fileNumber; ++i) {
            if (files[i].isDirectory()) {
                observerList.add(new SeafileObserver(monitorService, files[i].getPath()));
                recursiveDirectory(files[i].getPath());
            } else {
                continue;
            }
        }
        
    }
    
    
    public void startWatching() {
        for (int i = 0; i < observerList.size(); ++i) {          
            observerList.get(i).startWatching();
            Log.d("PATHS", observerList.get(i).getPath());
        }
        
    }
    
    public void stopWatching() {
        for (int i = 0; i < observerList.size(); ++i) {            
            observerList.get(i).stopWatching();
        }
        
    }
    
}
