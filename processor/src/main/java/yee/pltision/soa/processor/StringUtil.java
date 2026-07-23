package yee.pltision.soa.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>似乎没人提供这些轮子，只好自己造了</p>
 * <p>（其实我找轮子的时间要花半小时，而造一个只需要5分钟）</p>
 * <p>（也不一定，我总是希望代码能更规范）</p>
 * <p><i>这个class不是public是因为我期望你在编译后能完全剔除这个依赖</i></p>
 * @deprecated 因为 Hutool 而亡
 */
@Deprecated
class StringUtil {

    public static String firstUpper(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public static String firstLower(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    /**
     * <p>似乎只支持英文，但是够用了</p>
     * <p>处理逻辑似乎通用，因为firstLetter总是0</p>
     * <p><i>其实想加入一个 <code>isThisAClameName</code>??????? 如果不是按原样返回的判断，但还是懒了</i></p>
     * @param name <code>clameName</code> or <code>UpperClameName</code>
     * @return <code>CLAME_NAME</code>
     */
    //
    public static String clameName2UPPER_SNEAK(String name){
        List<String> words=new ArrayList<>();
        int firstLetter=0;
        if(name.length()<=1)
            return name;
        for(int i=0;i<name.length();i++){
            if(Character.isUpperCase(name.charAt(i))){
                firstLetter=i;
            }
            if(i>0&&(i+1==name.length()||Character.isUpperCase(name.charAt(i+1)))){
                words.add(name.substring(firstLetter,i+1));
            }
        }

        StringBuilder upper=new StringBuilder();
        Iterator<String> it = words.iterator();

        if(!it.hasNext())
            return "";

        for(;;){
            upper.append(it.next().toUpperCase());
            if(it.hasNext()){
                upper.append("_");
            }
            else {
                break;
            }
        }

        return upper.toString();
    }
}