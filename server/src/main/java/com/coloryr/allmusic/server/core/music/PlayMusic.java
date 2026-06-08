package com.coloryr.allmusic.server.core.music;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.IMusicApi;
import com.coloryr.allmusic.server.core.music.playback.PlaybackQueue;
import com.coloryr.allmusic.server.core.music.playback.PushService;
import com.coloryr.allmusic.server.core.music.playback.VoteService;
import com.coloryr.allmusic.server.core.objs.config.LimitObj;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.objs.music.MusicObj;
import com.coloryr.allmusic.server.core.objs.music.PlayerAddMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;
import com.coloryr.allmusic.server.core.saves.MusicListSave;
import com.coloryr.allmusic.server.core.utils.HudUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayMusic {
    private static final Object DEEP_LOCK = new Object();
    private static final PlaybackQueue playbackQueue = new PlaybackQueue();
    private static final VoteService voteService = new VoteService();
    private static final PushService pushService = new PushService();

    private static final Queue<PlayerAddMusicObj> tasks = new ConcurrentLinkedQueue<>();
    private static final Queue<MusicObj> deep = new ConcurrentLinkedQueue<>();
    /**
     * 正在播放的玩家
     */
    private static final Set<String> nowPlayPlayer = new HashSet<>();
    /**
     * 总歌曲长度
     */
    public static volatile long musicAllTime = 0;
    /**
     * 剩余歌曲长度
     */
    public static volatile long musicLessTime = 0;
    /**
     * 歌曲现在位置
     */
    public static volatile long musicNowTime = 0;
    /**
     * 当前歌曲信息
     */
    public static volatile SongInfoObj nowPlayMusic;
    /**
     * 当前歌词信息
     */
    public static volatile LyricSave lyric;
    /**
     * 播放链接
     */
    public static volatile String url;
    /**
     * 错误次数
     */
    public static volatile int error;
    /**
     * 空闲列表取出的歌曲序号
     */
    private static int idleIndex;

    /**
     * 开始歌曲逻辑
     */
    public static void start() {
        new Thread(PlayMusic::task, "allmusic_task").start();
    }

    /**
     * 添加投票的玩家
     *
     * @param player 用户名
     */
    public static void addVote(String player) {
        voteService.addVote(player);
    }

    public static void startVote(String player) {
        voteService.startVote(player);
    }

    /**
     * 添加投票的玩家
     *
     * @param player 用户名
     */
    public static void addPush(String player) {
        pushService.addPush(player);
    }

    public static void startPush(String player, SongInfoObj music) {
        pushService.startPush(player, music);
    }

    public static void pushTick() {
        pushService.pushTick();
    }

    public static void voteTick() {
        voteService.voteTick();
    }

    public static SongInfoObj getPush() {
        return pushService.getPush();
    }

    public static int getPushTime() {
        return pushService.getPushTime();
    }

    public static String getPushSender() {
        return pushService.getPushSender();
    }

    public static int getVoteTime() {
        return voteService.getVoteTime();
    }

    public static String getVoteSender() {
        return voteService.getVoteSender();
    }

    /**
     * 获取投票数量
     *
     * @return 数量
     */
    public static int getVoteCount() {
        return voteService.getVoteCount();
    }

    public static int getPushCount() {
        return pushService.getPushCount();
    }

    /**
     * 清空投票
     */
    public static void clearVote() {
        voteService.clearVote();
    }

    /**
     * 清空插歌
     */
    public static void clearPush() {
        pushService.clearPush();
    }

    /**
     * 是否已经投票了
     *
     * @param player 用户名
     * @return 结果
     */
    public static boolean containVote(String player) {
        return voteService.containVote(player);
    }

    public static boolean containPush(String player) {
        return pushService.containPush(player);
    }

    /**
     * 添加点歌任务
     *
     * @param obj 歌曲
     */
    public static void addTask(PlayerAddMusicObj obj) {
        tasks.add(obj);
    }

    private static void task() {
        AllMusic.log.data("歌曲处理线程启动");
        while (AllMusic.isRun) {
            try {
                PlayerAddMusicObj obj = tasks.poll();
                if (obj != null) {
                    IMusicApi api = AllMusic.getMusicApi(obj.api);
                    if (api != null) {
                        addMusic(obj.sender, obj.id, api, obj.name, obj.isDefault);
                    }
                }
                Thread.sleep(10);
            } catch (Exception e) {
                AllMusic.log.data("歌曲处理出现问题");
                e.printStackTrace();
            }
        }
        nowPlayPlayer.clear();
        voteService.clearVote();
        pushService.clearPush();
        playbackQueue.clear();
        clearVote();
        clearPush();

        AllMusic.log.data("歌曲处理线程关闭");
    }

    /**
     * 添加歌曲
     *
     * @param sender 发送者
     * @param id     歌曲ID
     * @param player 用户名
     * @param isList 是否是空闲歌单
     */
    public static void addMusic(Object sender, String id, IMusicApi api, String player, boolean isList) {
        if (haveMusic(id, api.getId()))
            return;
        if (sender != null) {
            String text = AllMusic.getMessage().musicPlay.checkMusic
                    .replace(ARG.musicId, id);
            AllMusic.side.sendMessageTask(sender, text);
        }
        AllMusic.log.data("<light_purple>[AllMusic3]<yellow>玩家：" + player + " 点歌：" + id);
        try {
            SongInfoObj info = api.getMusic(id, player, isList);
            if (info == null) {
                if (sender != null) {
                    String data = AllMusic.getMessage().musicPlay.emptyCanPlay;
                    AllMusic.side.sendMessageTask(sender, data.replace(ARG.musicId, id));
                }
                return;
            }
            LimitObj limit = AllMusic.getConfig().limit;
            if (limit.musicTimeLimit && info.getLength() / 1000 > limit.maxMusicTime) {
                if (sender != null) {
                    AllMusic.side.sendMessageTask(sender, AllMusic.getMessage().addMusic.timeOut);
                }
                return;
            }
            playbackQueue.add(info);
            if (!AllMusic.getConfig().muteAddMessage) {
                if (AllMusic.getConfig().showInBar) {
                    String data = AllMusic.getMessage().musicPlay.addMusic
                            .replace(ARG.musicName, HudUtils.messageLimit(info.getName()))
                            .replace(ARG.musicAuthor, HudUtils.messageLimit(info.getAuthor()))
                            .replace(ARG.musicAl, HudUtils.messageLimit(info.getAl()))
                            .replace(ARG.musicAlia, HudUtils.messageLimit(info.getAlia()))
                            .replace(ARG.player, info.getCall());
                    AllMusic.side.sendBarInTask(data);
                } else {
                    String data = AllMusic.getMessage().musicPlay.addMusic
                            .replace(ARG.musicName, info.getName())
                            .replace(ARG.musicAuthor, info.getAuthor())
                            .replace(ARG.musicAl, info.getAl())
                            .replace(ARG.musicAlia, info.getAlia())
                            .replace(ARG.player, info.getCall());
                    AllMusic.side.broadcastInTask(data);
                }
            }
            if (AllMusic.getConfig().playListSwitch
                    && (PlayMusic.nowPlayMusic != null && PlayMusic.nowPlayMusic.isList())) {
                PlayMusic.musicLessTime = 10;
                if (!isList) {
                    AllMusic.side.broadcastInTask(AllMusic.getMessage().musicPlay.switchMusic);
                }
            }
            error = 0;
        } catch (Exception e) {
            if (isList) {
                error++;
            }
            AllMusic.log.data("<light_purple>[AllMusic3]<red>歌曲信息解析错误");
            e.printStackTrace();
        }
    }

    /**
     * 将歌曲移动到队列头
     */
    public static void pushMusic() {
        SongInfoObj obj = pushService.getPush();
        playbackQueue.moveToFirst(obj);
    }

    /**
     * 获取播放列表长度
     *
     * @return 长度
     */
    public static int getListSize() {
        return playbackQueue.getListSize();
    }

    /**
     * 获取当前播放列表
     *
     * @return 播放列表
     */
    public static List<SongInfoObj> getList() {
        return playbackQueue.getList();
    }

    /**
     * 清理播放列表
     */
    public static void clear() {
        playbackQueue.clear();
    }

    /**
     * 从播放列表删除
     *
     * @param index 标号
     * @return 结果
     */
    public static SongInfoObj remove(int index) {
        return playbackQueue.remove(index);
    }

    /**
     * 从播放列表删除
     *
     * @param index
     */
    public static void remove(SongInfoObj index) {
        playbackQueue.remove(index);
    }

    /**
     * 获取播放列表所有信息
     *
     * @return 信息
     */
    public static String getAllList() {
        return playbackQueue.getAllList();
    }

    /**
     * 是否在播放列表中
     *
     * @param music 音乐
     * @return 结果
     */
    public static boolean haveMusic(MusicObj music) {
        return playbackQueue.haveMusic(music, nowPlayMusic);
    }

    /**
     * 是否在播放列表中
     *
     * @param id  音乐编号
     * @param api 音乐API编号
     * @return 是否在列表种
     */
    public static boolean haveMusic(String id, String api) {
        return playbackQueue.haveMusic(id, api, nowPlayMusic);
    }

    /**
     * 判断玩家点歌数量是否超上限
     *
     * @param name 玩家名
     * @return 是否超过上限
     */
    public static boolean isPlayerMax(String name) {
        int list = AllMusic.getConfig().maxPlayerList;
        if (list == 0) {
            return false;
        }
        int count = 0;
        for (PlayerAddMusicObj obj : tasks) {
            if (obj.name.equalsIgnoreCase(name)) {
                count++;
            }
        }

        return list <= count;
    }

    public static void clearIdleList() {
        deep.clear();
        MusicListSave.clearIdleList();
    }

    public static int getIdleListSize() {
        return MusicListSave.getListSize();
    }

    /**
     * 检查这个空闲歌是否已经放了
     *
     * @param music 空闲音乐
     * @return 是否已经放过了
     */
    private static boolean checkDeep(MusicObj music) {
        synchronized (DEEP_LOCK) {
            for (MusicObj obj : deep) {
                if (Objects.equals(obj.id, music.id) && Objects.equals(obj.api, music.api)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 获取空闲歌单的一首歌
     *
     * @return 结果
     */
    public static MusicObj getIdleMusic() {
        MusicObj music;
        int len = MusicListSave.getListSize();
        if (len == 0)
            return null;
        if (AllMusic.getConfig().playListRandom) {
            if (len == 1)
                return MusicListSave.readListItem();
            if (len > 10) {
                int size = AllMusic.getConfig().playListEscapeDeep;
                if (size > len / 2) {
                    size = len / 2;
                }
                while (deep.size() >= size) {
                    deep.poll();
                }
                do {
                    music = MusicListSave.readListItem();
                }
                while (checkDeep(music));
                deep.add(music);
            } else {
                music = MusicListSave.readListItem();
            }
        } else {
            music = MusicListSave.readListItem(idleIndex);
            idleIndex++;
            if (idleIndex >= len) {
                idleIndex = 0;
            }
        }
        return music;
    }

    public static SongInfoObj findPlayerMusic(String name) {
        return playbackQueue.findPlayerMusic(name);
    }

    public static SongInfoObj findMusicIndex(int index) {
        return playbackQueue.findMusicIndex(index);
    }

    /**
     * 获取正在播放的玩家列表
     *
     * @return 列表
     */
    public static Set<String> getNowPlayPlayer() {
        return nowPlayPlayer;
    }

    /**
     * 是否存在正在播放的玩家
     *
     * @param player 用户名
     * @return 是否存在
     */
    public static boolean containNowPlay(String player) {
        player = player.toLowerCase();
        return !nowPlayPlayer.contains(player);
    }

    /**
     * 添加正在播放的玩家
     *
     * @param player 用户名
     */
    public static void addNowPlayPlayer(String player) {
        player = player.toLowerCase();
        nowPlayPlayer.add(player);
    }

    /**
     * 删除正在播放的玩家
     *
     * @param player 用户名
     */
    public static void removeNowPlayPlayer(String player) {
        player = player.toLowerCase();
        nowPlayPlayer.remove(player);
    }

    /**
     * 清空正在播放玩家的列表
     */
    public static void clearNowPlayer() {
        nowPlayPlayer.clear();
    }
}

