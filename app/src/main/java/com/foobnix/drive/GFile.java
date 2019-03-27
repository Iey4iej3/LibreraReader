package com.foobnix.drive;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.IO;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.model.AppProfile;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.ui2.BooksService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GFile {
    public static final int REQUEST_CODE_SIGN_IN = 1110;

    public static final String MIME_FOLDER = "application/vnd.google-apps.folder";

    public static final String TAG = "GFile";
    public static final int PAGE_SIZE = 1000;
    public static final String SKIP = "skip";
    public static final String MY_SCOPE = DriveScopes.DRIVE_FILE;

    public static com.google.api.services.drive.Drive googleDriveService;

    public static String debugOut = new String();


    public static String getDisplayInfo(Context c) {
        final GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(c);
        if (account == null) {
            return "";
        }
        return account.getDisplayName() + " (" + account.getEmail() + ")";

    }

    public static void logout(Context c) {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(MY_SCOPE))
                        .build();
        GoogleSignInClient client = GoogleSignIn.getClient(c, signInOptions);
        client.signOut();
        googleDriveService = null;

    }

    public static void init(Activity c) {

        if (googleDriveService != null) {
            return;
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(c);

        if (account == null) {


            GoogleSignInOptions signInOptions =
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(new Scope(MY_SCOPE))
                            .build();
            GoogleSignInClient client = GoogleSignIn.getClient(c, signInOptions);

            // The result of the sign-in Intent is handled in onActivityResult.
            c.startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN);
        } else {

            GoogleAccountCredential credential =
                    GoogleAccountCredential.usingOAuth2(
                            c, Collections.singleton(MY_SCOPE));
            credential.setSelectedAccount(account.getAccount());
            googleDriveService =
                    new com.google.api.services.drive.Drive.Builder(
                            AndroidHttp.newCompatibleTransport(),
                            new GsonFactory(),
                            credential)
                            .setApplicationName(Apps.getApplicationName(c))
                            .build();
        }

    }

    public static void buildDriveService(Context c) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(c);
        if (account == null) {
            LOG.d(TAG, "buildDriveService", " account is null");
            return;
        }

        if (googleDriveService != null) {
            LOG.d(TAG, "googleDriveService", " has already inited");
            return;
        }


        GoogleAccountCredential credential =
                GoogleAccountCredential.usingOAuth2(
                        c, Collections.singleton(MY_SCOPE));
        credential.setSelectedAccount(account.getAccount());
        googleDriveService =
                new com.google.api.services.drive.Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName(Apps.getApplicationName(c))
                        .build();

        LOG.d(TAG, "googleDriveService", " build");

    }

    public static List<File> exeQF(String q, String... args) throws IOException {
        return exeQ(String.format(q, args));
    }

    public static List<File> exeQ(String q) throws IOException {
        //LOG.d(TAG, "exeQ", q);
        String nextPageToken = "";
        List<File> res = new ArrayList<File>();
        do {
            //debugOut += "\n:" + q;

            final FileList list = googleDriveService.files().list().setQ(q).setPageToken(nextPageToken).setFields("nextPageToken, files(*)").setPageSize(PAGE_SIZE).execute();
            nextPageToken = list.getNextPageToken();
            res.addAll(list.getFiles());
            debugOut += "\nGet remote files info: " + list.getFiles().size();
            //debugPrint(list.getFiles());
        } while (nextPageToken != null);
        return res;
    }

    public static List<File> getFiles(String rootId) throws Exception {

        //String time = new DateTime(lastModifiedTime).toString();
        LOG.d("getFiles-by", rootId);
        final String txt = "('%s' in parents and trashed = false) or ('%s' in parents and trashed = false and mimeType = '%s')";
        return exeQF(txt, rootId, rootId, MIME_FOLDER);
    }

    public static List<File> getFilesAll() throws Exception {
        return exeQ("trashed = false");
    }

    public static File findLibreraSync() throws Exception {

        final List<File> files = exeQF("name = 'Librera' and 'root' in parents and mimeType = '%s' and trashed = false", MIME_FOLDER);
        debugPrint(files);
        if (files.size() > 0) {
            return files.get(0);
        } else {
            return null;
        }
    }

    public static void debugPrint(List<File> list) {

        LOG.d(TAG, list.size());
        for (File f : list) {
            LOG.d(TAG, f.getId(), f.getName(), f.getMimeType(), f.getParents(), f.getCreatedTime(), f.getModifiedTime(), "trashed", f.getTrashed());
            LOG.d(f);
        }
    }

    public static File getFileById(String roodId, String name) throws IOException {
        LOG.d(TAG, "Get file", roodId, name);
        name = name.replace("'", "\\'");
        final List<File> files = exeQF("'%s' in parents and name='%s' and trashed = false", roodId, name);
        if (files != null && files.size() >= 1) {
            final File file = files.get(0);
            return file;
        }

        return null;
    }

    public static File getOrCreateLock(String roodId, long modifiedTime) throws IOException {
        File file = getFileById(roodId, "lock");
        if (file == null) {
            File metadata = new File()
                    .setParents(Collections.singletonList(roodId))
                    .setModifiedTime(new DateTime(modifiedTime))
                    .setMimeType("text/plain")
                    .setName("lock");

            LOG.d(TAG, "Create lock", roodId, "lock");
            debugOut += "\nCreate lock: " + new DateTime(modifiedTime).toStringRfc3339();
            file = googleDriveService.files().create(metadata).execute();
        }
        return file;
    }

    public static void updateLock(String roodId, long modifiedTime) throws IOException {
        File file = getOrCreateLock(roodId, modifiedTime);
        File metadata = new File().setModifiedTime(new DateTime(modifiedTime));

        debugOut += "\nUpdate lock: " + new DateTime(modifiedTime).toStringRfc3339();
        GFile.googleDriveService.files().update(file.getId(), metadata).execute();
    }

    public static File createFile(String roodId, String name, String content, long lastModifiedtime) throws IOException {
        File file = getFileById(roodId, name);
        if (file == null) {
            File metadata = new File()
                    .setParents(Collections.singletonList(roodId))
                    .setModifiedTime(new DateTime(lastModifiedtime))
                    .setMimeType("text/plain")
                    .setName(name);

            LOG.d(TAG, "Create file", roodId, name);
            file = googleDriveService.files().create(metadata).execute();
        }

        File metadata = new File().setName(name).setModifiedTime(new DateTime(lastModifiedtime));
        ByteArrayContent contentStream = ByteArrayContent.fromString("text/plain", content);
        LOG.d(TAG, "Create file with content", roodId, name);
        GFile.googleDriveService.files().update(file.getId(), metadata, contentStream).execute();

        return file;
    }


    public static File getFileInfo(String roodId, final java.io.File inFile) throws IOException {
        File file = getFileById(roodId, inFile.getName());
        if (file == null) {
            File metadata = new File()
                    .setParents(Collections.singletonList(roodId))
                    .setMimeType(ExtUtils.getMimeType(inFile))
                    .setModifiedTime(new DateTime(inFile.lastModified()))
                    .setName(inFile.getName());

            LOG.d(TAG, "Create file", roodId, inFile.getName());
            file = googleDriveService.files().create(metadata).execute();
        }
        return file;

    }

    public static File createFirstTime(String roodId, final java.io.File inFile) throws IOException {
        File metadata = new File()
                .setParents(Collections.singletonList(roodId))
                .setMimeType(ExtUtils.getMimeType(inFile))
                .setModifiedTime(new DateTime(inFile.lastModified()))
                .setName(inFile.getName());

        LOG.d(TAG, "Create file", roodId, inFile.getName());
        return googleDriveService.files().create(metadata).execute();
    }


    public static void uploadFile(String roodId, String fileId, final java.io.File inFile) throws IOException {
        debugOut += "\nUpload: " + inFile.getParentFile().getName() + "/" + inFile.getName();
        File metadata = new File().setName(inFile.getName()).setModifiedTime(new DateTime(inFile.lastModified()));
        FileContent contentStream = new FileContent("text/plain", inFile);
        LOG.d(TAG, "Upload: " + inFile.getParentFile().getName() + "/" + inFile.getName());
        googleDriveService.files().update(fileId, metadata, contentStream).execute();
    }


    public static String readFileAsString(String fileId) throws IOException {

        LOG.d(TAG, "read file as string", fileId);
        //File metadata = googleDriveService.files().get(fileId).execute();
        //String name = metadata.getName();

        // Stream the file contents to a String.
        try (InputStream is = googleDriveService.files().get(fileId).executeMediaAsInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            String contents = stringBuilder.toString();

            return contents;
        }


    }


    public static void downloadFile(String fileId, java.io.File file, long lastModified) throws IOException {
        LOG.d(TAG, "Download: " + file.getParentFile().getName() + "/" + file.getName());
        debugOut += "\nDownload: " + file.getParentFile().getName() + "/" + file.getName();
        InputStream is = null;
        //if (!file.getPath().endsWith("json")) {
        //    is = googleDriveService.files().get(fileId).executeMediaAsInputStream();
        //} else {
        // }
        try {
            is = googleDriveService.files().get(fileId).executeMediaAsInputStream();
        } catch (IOException e) {
            is = googleDriveService.files().get(fileId).executeAsInputStream();
        }

        IO.copyFile(is, file);
        //LOG.d(TAG, "downloadFile-lastModified before", file.lastModified(), lastModified, file.getName());

        setLastModifiedTime(file, lastModified);

        //LOG.d(TAG, "downloadFile-lastModified after", file.lastModified(), lastModified, file.getName());

    }

    @TargetApi(Build.VERSION_CODES.O)
    public static boolean setLastModifiedTime(java.io.File file, long lastModified) {
        if (lastModified > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.setLastModifiedTime(Paths.get(file.getPath()), FileTime.fromMillis(lastModified));
            } catch (IOException e) {
                LOG.e(e);
                return false;
            }
        }
        return true;
    }

    public static void deleteBook(String rootId, String name) throws IOException {
        name = name.replace("'", "\\'");
        final List<File> files = exeQF("'%s' in parents and name = '%s'", rootId, name);
        for (File f : files) {
            File metadata = new File().setTrashed(true);
            LOG.d("Delete book", name);
            debugOut += "\nDelete book: " + name;
            googleDriveService.files().update(f.getId(), metadata).execute();
        }
    }


    public static File createFolder(String roodId, String name) throws IOException {
        File folder = getFileById(roodId, name);
        if (folder != null) {
            return folder;
        }
        LOG.d(TAG, "Create folder", roodId, name);
        debugOut += "\nCreate remote folder: " + name;
        File metadata = new File()
                .setParents(Collections.singletonList(roodId))
                //.setModifiedTime(new DateTime(lastModified))
                .setMimeType(MIME_FOLDER)
                .setName(name);

        return googleDriveService.files().create(metadata).execute();

    }


    public static synchronized void sycnronizeAll(final Context c) throws Exception {

        try {
            debugOut = "";
            buildDriveService(c);
            LOG.d(TAG, "sycnronizeAll", "begin");
            if (TxtUtils.isEmpty(BookCSS.get().syncRootID)) {
                File syncRoot = GFile.findLibreraSync();
                LOG.d(TAG, "findLibreraSync finded", syncRoot);
                if (syncRoot == null) {
                    syncRoot = GFile.createFolder("root", "Librera");
                }
                BookCSS.get().syncRootID = syncRoot.getId();
                BookCSS.get().save(c);
            }


            debugOut += "\nBegin";
            LOG.d("Begin");

            sync(BookCSS.get().syncRootID, AppProfile.SYNC_FOLDER_ROOT);

            //updateLock(AppState.get().syncRootID, beginTime);

            LOG.d(TAG, "sycnronizeAll", "finished");
            debugOut += "\nEnd";

        } catch (IOException e) {
            debugOut += "\nException: " + e.getMessage();
            LOG.e(e);
            throw e;
        }
    }

    private static void sync(String syncId, final java.io.File ioRoot) throws Exception {
        final List<File> driveFiles = getFilesAll();
        LOG.d(TAG, "getFilesAll", "end");

        Map<String, File> map = new HashMap<>();
        Map<java.io.File, File> map2 = new HashMap<>();

        for (File file : driveFiles) {
            map.put(file.getId(), file);
        }
        for (File file : driveFiles) {
            final String filePath = findFile(file, map);

            if (filePath.startsWith(SKIP)) {
                continue;
            }

            java.io.File local = new java.io.File(ioRoot, filePath);
            map2.put(local, file);
        }
        //upload second files
        for (File remote : driveFiles) {
            //create local files
            if (!MIME_FOLDER.equals(remote.getMimeType())) {
                final String filePath = findFile(remote, map);
                if (filePath.startsWith(SKIP)) {
                    LOG.d(TAG, "Skip", filePath);
                    continue;
                }

                java.io.File local = new java.io.File(ioRoot, filePath);
                if (!local.exists() || remote.getModifiedTime().getValue() / 1000 > local.lastModified() / 1000) {
                    final java.io.File parentFile = local.getParentFile();
                    if (parentFile.exists()) {
                        parentFile.mkdirs();
                    }
                    downloadFile(remote.getId(), local, remote.getModifiedTime().getValue());
                }
            }
        }

        syncUpload(syncId, ioRoot, map2);
    }

    private static void syncUpload(String syncId, java.io.File ioRoot, Map<java.io.File, File> map2) throws IOException {
        java.io.File[] files = ioRoot.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File local : files) {
            File remote = map2.get(local);
            if (local.isDirectory()) {
                if (remote == null) {
                    remote = createFolder(syncId, local.getName());
                }
                syncUpload(remote.getId(), local, map2);
            } else {
                if (remote == null) {
                    File add = createFirstTime(syncId, local);
                    uploadFile(syncId, add.getId(), local);
                } else if (remote.getModifiedTime().getValue() / 1000 < local.lastModified() / 1000) {
                    uploadFile(syncId, remote.getId(), local);
                }


            }
        }
    }

//    private static void syncUpload(String syncId, final java.io.File ioRoot, List<File> driveFiles) throws Exception {
//        LOG.d(TAG, "updaload", ioRoot.getName());
//        final java.io.File[] files = ioRoot.listFiles();
//        for (java.io.File file : files) {
//            final String filePath = findFile(remote, map);
//
//
//        }


//        for (java.io.File file : toUpload) {
//            if (file.isDirectory()) {
//                File folder = createFolder(syncId, file.getName(), file.lastModified());
//                sync(folder.getId(), file);
//            } else if (file.isFile()) {
//                final File syncFile = getFileInfo(syncId, file);
//                uploadFile(syncId, syncFile.getId(), file);
//            }
//        }
//    }
//
//        for (File remote : driveFiles) {
//        java.io.File local = new java.io.File(ioRoot, remote.getName());
//        if (MIME_FOLDER.equals(remote.getMimeType())) {
//            if (!local.exists()) {
//                debugOut += "\nCreate local folder: " + local.getName();
//                local.mkdirs();
//                setLastModifiedTime(local, remote.getModifiedTime().getValue());
//            }
//            sync(remote.getId(), local);
//        } else {
//            if (!local.exists() || remote.getModifiedTime().getValue() / 1000 > local.lastModified() / 1000) {
//                downloadFile(remote.getId(), local, remote.getModifiedTime().getValue());
//            } else if (remote.getModifiedTime().getValue() / 1000 < local.lastModified() / 1000) {
//                uploadFile(syncId, remote.getId(), local);
//            }
//        }
//    }
    //   }

    private static String findFile(File file, Map<String, File> map) {
        if (file == null) {
            return SKIP;
        }
        if (file.getParents() == null) {
            return SKIP;
        }

        if (file.getId().equals(BookCSS.get().syncRootID)) {
            return "";
        }

        return findFile(map.get(file.getParents().get(0)), map) + "/" + file.getName();
    }


    public static void runSyncService(Activity a) {
        if (BookCSS.get().isEnableGdrive && !BooksService.isRunning) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(a);
            if (account != null) {
                GFile.buildDriveService(a);
                a.startService(new Intent(a, BooksService.class).setAction(BooksService.ACTION_RUN_SYNCRONICATION));
            }
        }

    }

}

