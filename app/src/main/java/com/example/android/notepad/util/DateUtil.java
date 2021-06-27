package com.example.android.notepad.util;

import java.text.SimpleDateFormat;
import java.util.Date;


//返回时间类
public class DateUtil {
    public static String StringToDate(String str_data)
    {
        String beginDate=str_data;
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        String sd = sdf.format(new Date(Long.parseLong(beginDate)));
        return  sd;
    }
}
