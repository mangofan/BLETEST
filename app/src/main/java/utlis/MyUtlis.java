package utlis;
/*
 * Created by fanwe on 2017/5/23.
 */

import android.util.SparseArray;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.math.BigDecimal.ROUND_HALF_UP;

public class MyUtlis {
    private static double varianceLimit = 0.5;   //对于方差的限制

    //使用质心定位得到坐标,使用BigDecimal来减少将6.7表示为6.6999999999的情况
    public static String getMassCenter(SparseArray<ArrayList<String>> SortedNodeMacAndRssi, Map<String, String> bleNodeLoc) {
        ArrayList<String> SortedNodeMacList = SortedNodeMacAndRssi.get(1);   //获取排好序的节点的MAC地址的列表
        ArrayList<String> SortedNodeRssiList = SortedNodeMacAndRssi.get(2);  //获取排好序的节点的RSSI地址的列表
        int lenOfMacAndRssi = SortedNodeMacList.size();   //首先获取节点列表的长度
        BigDecimal[] massCenter = new BigDecimal[2];
        massCenter[0] = new BigDecimal("0.0");
        massCenter[1] = new BigDecimal("0.0");
        for (int i = lenOfMacAndRssi; i > 0; i--) {    //从多到少，分别计算方差，方差小于某个值时，认为这几个值相近，求这几个值的质心
            ArrayList<String> rssiList = cutList(SortedNodeRssiList, i);
            ArrayList<String> macList = cutList(SortedNodeMacList, i);
            double variance = getVariance(rssiList, getAvg(rssiList), "not sure");
            if (variance < varianceLimit) {
                for (int j = 0; j < i; j++) {
                    BigDecimal[] node = new BigDecimal[2];
                    String[] location = bleNodeLoc.get(macList.get(j)).split(",");
                    node[0] = new BigDecimal(location[0]);
                    node[1] = new BigDecimal(location[1]);
                    massCenter[0] = massCenter[0].add(node[0]);
                    massCenter[1] = massCenter[1].add(node[1]);
                }
                massCenter[0] = massCenter[0].divide(new BigDecimal(String.valueOf(i)), 1, ROUND_HALF_UP);
                massCenter[1] = massCenter[1].divide(new BigDecimal(String.valueOf(i)), 1, ROUND_HALF_UP);
                break;
            }
        }
        return massCenter[0] + "," + massCenter[1];
    }

    //根据RSSI强度，对MAC地址排序
    public static SparseArray<ArrayList<String>> sortNodeBasedOnRssi(Map<String, Double> mRssiFilterd, int BLE_CHOOSED_NUM) {
        List<Map.Entry<String, Double>> infoIds = new ArrayList<>(mRssiFilterd.entrySet());
        ArrayList<String> listOfMac = new ArrayList<>();
        ArrayList<String> listOfRssi = new ArrayList<>();
        ArrayList<String> listOfVariance = new ArrayList<>();
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
        for(int i = 2; i<=limit; i++){
            ArrayList<String> listRssi = cutList(listOfRssi, i);
            double variance = getVariance(listRssi, getAvg(listRssi), "not sure");
            listOfVariance.add(String.valueOf(variance));
        }

        SparseArray<ArrayList<String>> toReturn = new SparseArray<>();
        toReturn.put(1, listOfMac);
        toReturn.put(2, listOfRssi);
        toReturn.put(3, listOfVariance);
        return toReturn;     //排序好的MAC地址的列表
    }

    //求ArrayList方差
    private static double getVariance(ArrayList list, Double avg, String distribution) {
        double variance = 0.0;
        if (list.size() > 1) {
            for (int i = 0; i < list.size(); i++) {
                variance += Math.pow((Double.valueOf(list.get(i).toString()) - avg), 2);
            }
            if (distribution.equals("logarNormal"))
                variance = variance / list.size();
            else
                variance = variance / list.size();
        }
        return variance;
    }

    //切割子list
    public static <T> ArrayList<T> cutList(List<T> list, int limit) {
        int trueLimit = limit < list.size() ? limit : list.size();
        ArrayList<T> returnList = new ArrayList<>();
        for (int i = 0; i < trueLimit; i++) {
            returnList.add(list.get(i));
        }
        return returnList;
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
                stadardDev = Math.sqrt(stadardDev / list.size());
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