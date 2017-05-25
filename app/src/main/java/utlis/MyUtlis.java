package utlis;
/*
 * Created by fanwe on 2017/5/23.
 */

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MyUtlis {

    //根据RSSI强度，对MAC地址排序
    public static SparseArray<ArrayList<String>> sortNodeBasedOnRssi(Map<String, Double> mRssiFilterd, int BLE_CHOOSED_NUM) {
        List<Map.Entry<String, Double>> infoIds = new ArrayList<>(mRssiFilterd.entrySet());
        ArrayList<String> listOfMac = new ArrayList<>();
        ArrayList<String> listOfRssi = new ArrayList<>();
        int limit = BLE_CHOOSED_NUM < mRssiFilterd.size() ? BLE_CHOOSED_NUM : mRssiFilterd.size();

        Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {        //排序
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        for (int i = 0; i < limit; i++) {        //排序完,取前limit个
            String[] id = infoIds.get(i).toString().split("=");   //string.split后变为字符串数组。
            listOfMac.add(id[0]);
            listOfRssi.add(id[1]);
        }
        SparseArray<ArrayList<String>> toReturn = new SparseArray<>();
        toReturn.put(1, listOfMac);
        toReturn.put(2, listOfRssi);
        return toReturn;     //排序好的MAC地址的列表
    }

    //对数正态滤波
    public static double LogarNormalDistribution(ArrayList<Double> mAllRssilist, int RSSI_LIMIT) {
        ArrayList<Double> rssiList = cutListAndDleteTo15(mAllRssilist, RSSI_LIMIT);  //按照limit切割子list，并删除切割后剩余的值
        Double avg, staDev, proLowLim, proHighLim, pdfAltered;  //rssiValue作为一个中间变量在多个计算过程中间使用

        ArrayList<Double> logarNormalList = GetLogarNormalList(rssiList);   //转换成对数形式
        avg = getAvg(logarNormalList);   //求均值
        staDev = getStaDev(logarNormalList, avg, "logarNormal");  //求标准差
        ArrayList<Double> dataToGetAvg = new ArrayList<>();
        if (staDev != 0) {    //取值的标准差不为零时，去除低概率值，再计算平均值
            proHighLim = Math.exp(0.5 * Math.pow(staDev, 2) - avg) / (staDev * Math.sqrt(2 * Math.PI));
            proLowLim = proHighLim * 0.6;
            double denominatorOfExponent = 2 * Math.pow(staDev, 2);  //提前计算exponent的分母，因为历次分母相同，避免重复计算
            double partDenominatorOfPdfAltered = staDev * Math.sqrt(2 * Math.PI);   //提前计算pdfAltered的一部分分母，也是为了避免重复计算

            for (int i = 0; i < logarNormalList.size(); i++) {          //去掉value中的低概率RSSI，并且计算剩余的平均值。
                double exponent = -Math.pow(logarNormalList.get(i) - avg, 2) / denominatorOfExponent;
                pdfAltered = Math.exp(exponent) / ((0 - rssiList.get(i)) * partDenominatorOfPdfAltered);
                if (pdfAltered > proLowLim && pdfAltered < proHighLim) {   //筛选在高概率区域内的数据
                    dataToGetAvg.add(rssiList.get(i));
                }
            }
            return getAvg(dataToGetAvg);
        }else             //取值的标准差为零时，直接返回平均值
            return getAvg(rssiList);
    }

    //求ArrayLIst均值
    public static double getAvg(List list) {
        double sum = 0.0, avg = 0.0;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += Double.valueOf(list.get(i).toString());
            }
            avg = sum / list.size();
        }
        return avg;
    }

    //求ArrayList标准差
    public static double getStaDev(ArrayList list, Double avg, String distribution) {
        double stadardDev = 0.0;
        if (list.size() > 1) {
            for (int i = 0; i < list.size(); i++) {
                stadardDev += Math.pow((Double.valueOf(list.get(i).toString()) - avg), 2);
            }
            if (distribution.equals("logarNormal"))
                stadardDev = Math.sqrt(stadardDev / list.size());
            else
                stadardDev = Math.sqrt(stadardDev / (list.size() - 1));
        }
        return stadardDev;
    }

    //对ArrayList每个值取对数，以应用于对数正态运算的函数
    private static ArrayList<Double> GetLogarNormalList(ArrayList<Double> list) {
        ArrayList<Double> list1 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            list1.add(Math.log(0 - list.get(i)));
        }
        return list1;
    }

    //切割子list，并且去掉之后需要的值之后的值
    private static <T> void cutListAndDelete(List<T> list, int limit){
        if(list.size() > limit){
            for(int i = list.size()-1; i > limit-1; i--){
                list.remove(i);
            }
        }
    }

    //切割子listy
    public static <T> ArrayList<T> cutListAndDleteTo15(List<T> list, int limit) {
        int trueLimit = limit < list.size() ? limit : list.size();
        ArrayList<T> returnList = new ArrayList<>();
        for (int i = 0; i < trueLimit; i++) {
            returnList.add(list.get(i));
        }
        if(list.size() > 15){
            list.remove(15);
        }
        return returnList;
    }

}