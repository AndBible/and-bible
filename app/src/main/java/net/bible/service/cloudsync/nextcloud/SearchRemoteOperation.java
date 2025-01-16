/*
 * Nextcloud Android Library
 *
 * SPDX-FileCopyrightText: 2017-2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: MIT
 */
package net.bible.service.cloudsync.nextcloud;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.WebDavFileUtils;
import com.owncloud.android.lib.resources.files.NcSearchMethod;
import com.owncloud.android.lib.resources.files.model.RemoteFile;
import com.owncloud.android.lib.resources.status.OCCapability;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;
import org.apache.jackrabbit.webdav.search.SearchInfo;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote operation performing the search in the Nextcloud server.
 */
public class SearchRemoteOperation extends RemoteOperation<List<RemoteFile>> {


    public enum SearchType {
        FILE_SEARCH, // search by name
        FAVORITE_SEARCH, // get all favorite files/folder
        RECENTLY_MODIFIED_SEARCH, // get files/folders that were modified within last 7 days, ordered descending by time
        PHOTO_SEARCH, // gets all files with mimetype "image/%"
        /**
         * @deprecated unused, to be removed in a future version
         */
        @Deprecated
        SHARED_SEARCH, // show all shares
        GALLERY_SEARCH, // combined photo and video
        FILE_ID_SEARCH, // search one file specified by file id
        CONTENT_TYPE_SEARCH,
        RECENTLY_ADDED_SEARCH,
        SHARED_FILTER
    }

    private final String searchQuery;
    private final SearchType searchType;
    private final boolean filterOutFiles;
    private int limit;
    private long timestamp = -1;
    private final OCCapability capability;
    private Long startDate = null;
    private Long endDate = null;

    public SearchRemoteOperation(String query,
                                 SearchType searchType,
                                 boolean filterOutFiles,
                                 final OCCapability capability) {
        this.searchQuery = query;
        this.searchType = searchType;
        this.filterOutFiles = filterOutFiles;
        this.capability = capability;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public void setStartDate(Long startDate) {
        this.startDate = startDate;
    }
    
    public void setEndDate(Long endDate) {
        this.endDate = endDate;
    }

    @Override
    protected RemoteOperationResult<List<RemoteFile>> run(OwnCloudClient client) {
        RemoteOperationResult<List<RemoteFile>> result;
        com.owncloud.android.lib.resources.files.NcSearchMethod searchMethod = null;
        OptionsMethod optionsMethod;

        String webDavUrl = client.getDavUri().toString();
        optionsMethod = new OptionsMethod(webDavUrl);

        try {
            int optionsStatus = client.executeMethod(optionsMethod);
            boolean isSearchSupported = optionsMethod.isAllowed("SEARCH");

            if (isSearchSupported) {
                searchMethod = new NcSearchMethod(webDavUrl,
                                                  new SearchInfo("NC",
                                                                 Namespace.XMLNS_NAMESPACE,
                                                                 searchQuery),
                                                  searchType,
                                                  getClient().getUserIdPlain(),
                                                  timestamp,
                                                  limit,
                                                  filterOutFiles,
                                                  capability,
                                                  startDate,
                                                  endDate);

                int status = client.executeMethod(searchMethod);

                // check and process response
                boolean isSuccess = (status == HttpStatus.SC_MULTI_STATUS || status == HttpStatus.SC_OK);

                if (isSuccess) {
                    // get data from remote folder
                    MultiStatus dataInServer = searchMethod.getResponseBodyAsMultiStatus();
                    WebDavFileUtils webDavFileUtils = new WebDavFileUtils();
                    ArrayList<RemoteFile> mFolderAndFiles = webDavFileUtils.readData(dataInServer,
                            client,
                            false,
                            true);

                    // Result of the operation
                    result = new RemoteOperationResult<>(true, status, searchMethod.getResponseHeaders());
                    // Add data to the result
                    if (result.isSuccess()) {
                        result.setResultData(mFolderAndFiles);
                    }
                } else {
                    // synchronization failed
                    client.exhaustResponse(searchMethod.getResponseBodyAsStream());
                    result = new RemoteOperationResult<>(false, status, searchMethod.getResponseHeaders());
                }
            } else {
                client.exhaustResponse(optionsMethod.getResponseBodyAsStream());
                result = new RemoteOperationResult<>(false, optionsStatus, optionsMethod.getResponseHeaders());
            }

        } catch (Exception e) {
            result = new RemoteOperationResult<>(e);
        } finally {
            if (searchMethod != null) {
                searchMethod.releaseConnection();  // let the connection available for other methods
            }

            optionsMethod.releaseConnection();
        }
        return result;
    }
}

