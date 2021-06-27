package com.example.android.notepad.application;

import android.app.Application;
import android.content.Context;

import com.example.android.notepad.util.SharedPreferenceUtil;

public class MyApplication extends Application {

    private static Context context;

    //背景颜色的十六进制值,默认为白色

    private static String background="#ffffff";

    @Override
    public void onCreate()
    {
        super.onCreate();
        context=getApplicationContext();
        readBackground();
    }

    public static Context getContext() {
        return context;
    }

    public static String getBackground() {
        return background;
    }


        //读取配置文件中的背景颜色

    public static void readBackground(){
        if(SharedPreferenceUtil.getDate("background")==null||SharedPreferenceUtil.getDate("background").equals("")){

        }
        else{
            background= SharedPreferenceUtil.getDate("background");
        }

    }

}