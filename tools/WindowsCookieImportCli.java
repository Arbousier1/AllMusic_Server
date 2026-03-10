package com.coloryr.allmusic.server.tools;

import com.coloryr.allmusic.server.core.utils.WindowsCookieImporter;

import java.io.File;
import java.util.List;

public class WindowsCookieImportCli {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: <api> <cookieFile>");
            System.exit(2);
            return;
        }

        String api = WindowsCookieImporter.normalizeApi(args[0]);
        File cookieFile = new File(args[1]);
        List<com.coloryr.allmusic.server.core.objs.CookieObj> list =
                WindowsCookieImporter.importToFile(api, cookieFile);
        System.out.println("Imported " + list.size() + " cookies for " + api + " into " + cookieFile.getAbsolutePath());
    }
}
