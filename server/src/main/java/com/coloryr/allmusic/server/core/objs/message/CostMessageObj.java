package com.coloryr.allmusic.server.core.objs.message;

public class CostMessageObj {
    public String search;
    public String addMusic;
    public String noMoney;
    public String costFail;

    public static CostMessageObj make() {
        CostMessageObj obj = new CostMessageObj();
        obj.init();

        return obj;
    }

    public boolean check() {
        if (search == null)
            return true;
        if (addMusic == null)
            return true;
        if (noMoney == null)
            return true;
        return costFail == null;
    }

    public void init() {
        if (search == null)
            search = "<light_purple>[AllMusic]<yellow>你搜歌花费了" + ARG.cost;
        if (addMusic == null)
            addMusic = "<light_purple>[AllMusic]<yellow>你点歌花费了" + ARG.cost;
        if (noMoney == null)
            noMoney = "<light_purple>[AllMusic]<red>你没有足够的钱";
        if (costFail == null)
            costFail = "<light_purple>[AllMusic]<red>扣钱过程中错误";
    }
}
