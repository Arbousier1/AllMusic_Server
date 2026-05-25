package com.coloryr.allmusic.server.core.objs;

public class SearchMusicObj {
    public final String id;
    public final String name;
    public final String author;
    public final String al;
    public final String api;

    public SearchMusicObj(String ID, String Name, String Author, String Al) {
        this(ID, Name, Author, Al, null);
    }

    public SearchMusicObj(String ID, String Name, String Author, String Al, String Api) {
        this.id = ID;
        this.name = Name;
        this.author = Author;
        this.al = Al;
        this.api = Api;
    }
}
