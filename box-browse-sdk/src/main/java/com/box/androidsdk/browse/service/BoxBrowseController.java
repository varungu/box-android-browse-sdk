package com.box.androidsdk.browse.service;

import android.content.Context;
import android.util.DisplayMetrics;
import android.widget.Toast;

import com.box.androidsdk.browse.R;
import com.box.androidsdk.browse.uidata.ThumbnailManager;
import com.box.androidsdk.content.BoxApiFile;
import com.box.androidsdk.content.BoxApiFolder;
import com.box.androidsdk.content.BoxApiSearch;
import com.box.androidsdk.content.BoxFutureTask;
import com.box.androidsdk.content.models.BoxFolder;
import com.box.androidsdk.content.models.BoxSession;
import com.box.androidsdk.content.requests.BoxRequest;
import com.box.androidsdk.content.requests.BoxRequestsFile;
import com.box.androidsdk.content.requests.BoxRequestsFolder;
import com.box.androidsdk.content.requests.BoxRequestsSearch;
import com.box.androidsdk.content.requests.BoxResponse;
import com.box.androidsdk.content.utils.BoxLogUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/***
 * Default implementation for the {@link BrowseController}.
 */
public class BoxBrowseController implements BrowseController {
    private static final String TAG = BoxBrowseController.class.getName();

    // Static executors so that requests can be retained though activity/fragment lifecycle
    private static ThreadPoolExecutor mApiExecutor;
    private static ThreadPoolExecutor mThumbnailExecutor;

    protected final BoxApiFile mFileApi;
    protected final BoxApiFolder mFolderApi;
    protected final BoxApiSearch mSearchApi;
    protected final BoxSession mSession;
    protected final ThumbnailManager mThumbnailManager;
    protected BoxFutureTask.OnCompletedListener mListener;


    public BoxBrowseController(BoxSession session, BoxApiFile apiFile, BoxApiFolder apiFolder, BoxApiSearch apiSearch) {
        mSession = session;
        mFileApi = apiFile;
        mFolderApi = apiFolder;
        mSearchApi = apiSearch;
        mThumbnailManager = createThumbnailManager(mSession);
    }

    public BoxBrowseController(BoxSession session) {
        mSession = session;
        mFileApi = new BoxApiFile(mSession);
        mFolderApi =  new BoxApiFolder(mSession);
        mSearchApi =  new BoxApiSearch(mSession);
        mThumbnailManager = createThumbnailManager(mSession);
    }

    private ThumbnailManager createThumbnailManager(BoxSession session) {
        try {
            return new ThumbnailManager(this);
        } catch (FileNotFoundException e) {
            BoxLogUtils.e(TAG, e);
        }
        return null;
    }

    @Override
    public BoxRequestsFolder.GetFolderWithAllItems getFolderWithAllItems(String folderId) {
        return mFolderApi.getFolderWithAllItems(folderId)
                .setFields(BoxFolder.ALL_FIELDS);
    }

    @Override
    public BoxRequestsSearch.Search getSearchRequest(String query) {
        return mSearchApi.getSearchRequest(query);
    }

    @Override
    public BoxRequestsFile.DownloadThumbnail getThumbnailRequest(String fileId, File downloadFile) {
        DisplayMetrics metrics = mSession.getApplicationContext().getResources().getDisplayMetrics();
        int thumbSize = BoxRequestsFile.DownloadThumbnail.SIZE_128;
        if (metrics.density <= DisplayMetrics.DENSITY_MEDIUM) {
            thumbSize = BoxRequestsFile.DownloadThumbnail.SIZE_64;
        } else if (metrics.density <= DisplayMetrics.DENSITY_HIGH) {
            thumbSize = BoxRequestsFile.DownloadThumbnail.SIZE_64;
        }
        try {
            return mFileApi.getDownloadThumbnailRequest(downloadFile, fileId)
                    .setMinWidth(1024)
                    .setMinHeight(1024);
        } catch (IOException e) {
            BoxLogUtils.e(TAG, e);
        }
        return null;
    }

    @Override
    public void execute(BoxRequest request) {
        if (request == null) {
            return;
        }

        BoxFutureTask task = request.toTask();
        if (mListener != null) {
            task.addOnCompletedListener(mListener);
        }

        // Thumbnail request should be executed in their own executor pool
        ThreadPoolExecutor executor = request instanceof BoxRequestsFile.DownloadThumbnail ?
                getThumbnailExecutor() :
                getApiExecutor();
        executor.submit(task);
    }

    @Override
    public BrowseController setCompletedListener(BoxFutureTask.OnCompletedListener listener) {
        mListener = listener;
        return this;
    }

    @Override
    public void onError(Context context, BoxResponse response) {
        if (response.getRequest() instanceof BoxRequestsFolder.GetFolderWithAllItems) {
            Toast.makeText(context, R.string.box_browsesdk_problem_fetching_folder, Toast.LENGTH_LONG);
        } else if (response.getRequest() instanceof BoxRequestsSearch.Search) {
            Toast.makeText(context, R.string.box_browsesdk_problem_performing_search, Toast.LENGTH_LONG);
        }
    }

    @Override
    public File getThumbnailCacheDir() {

        // Create box thumbnail directory.
        // This should be same as in preview to ensure preview can use thumbnails from here
        File thumbnailDirectory = new File(mSession.getCacheDir(), "BoxThumbnails");
        if (!thumbnailDirectory.exists()) {
            thumbnailDirectory.mkdir();
        }

        return thumbnailDirectory;
    }

    @Override
    public ThumbnailManager getThumbnailManager() {
        return mThumbnailManager;
    }

    protected ThreadPoolExecutor getApiExecutor() {
        if (mApiExecutor == null || mApiExecutor.isShutdown()) {
            mApiExecutor = new ThreadPoolExecutor(1, 1, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        }
        return mApiExecutor;
    }

    /**
     * Executor that we will submit thumbnail tasks to.
     *
     * @return executor
     */
    @Override
    public ThreadPoolExecutor getThumbnailExecutor() {
        if (mThumbnailExecutor == null || mThumbnailExecutor.isShutdown()) {
            mThumbnailExecutor = new ThreadPoolExecutor(1, 10, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        }
        return mThumbnailExecutor;
    }

    @Override
    public void Log(String tag, String msg, Throwable t) {
        BoxLogUtils.e(tag, msg, t);
    }
}
