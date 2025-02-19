/*******************************************************************************
 * Copyright (c) 2017, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.repository.transport.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

import com.ibm.ws.repository.common.enums.StateAction;
import com.ibm.ws.repository.transport.client.DataModelSerializer.Verification;
import com.ibm.ws.repository.transport.exceptions.BadVersionException;
import com.ibm.ws.repository.transport.exceptions.ClientFailureException;
import com.ibm.ws.repository.transport.exceptions.RequestFailureException;
import com.ibm.ws.repository.transport.model.AppliesToFilterInfo;
import com.ibm.ws.repository.transport.model.Asset;
import com.ibm.ws.repository.transport.model.Attachment;
import com.ibm.ws.repository.transport.model.AttachmentSummary;
import com.ibm.ws.repository.transport.model.CopyUtils;
import com.ibm.ws.repository.transport.model.WlpInformation;

/**
 * A repository client which reads the metadata for all of the assets in the repository from a single JSON file.
 * <p>
 * Implementation notes:
 * <ul>
 * <li>This client caches the parsed JSON from the file. It will re-read the whole file if it has been modified since it was last read to support tests which write assets and then
 * read them back</li>
 * <li>The JSON is only converted to an Asset when required. This allows us to throw a {@link BadVersionException} in response to a call to {@link #getAsset(String)}</li>
 * <li>The asset id is its index within the JSON file. This means we don't have to actually store an ID in the JSON file, but does mean we need to maintain the order of the file
 * and leave spaces in the file if an asset is deleted.</li>
 * </ul>
 */
public class SingleFileClient extends AbstractRepositoryClient implements RepositoryWriteableClient {

    private final File file;
    private AtomicInteger idCounter;
    private final FileDataCache<Map<String, JsonObject>> assetMapCache;
    private final FileDataCache<Collection<Asset>> allAssetsCache;

    /**
     * Create a SingleFileClient instance
     * <p>
     * The file will not be read and parsed until it's needed.
     * <p>
     * If the file does not exist, it will be treated as an empty repository and written when {@link #addAsset(Asset)} is called.
     *
     * @param jsonFile the JSON file which holds all the asset metadata
     */
    public SingleFileClient(File jsonFile) {
        this.file = jsonFile;
        this.assetMapCache = new FileDataCache<>(jsonFile, this);
        this.allAssetsCache = new FileDataCache<>(jsonFile, this);
    }

    /**
     * Return a map from id to JsonObject representing an Asset
     * <p>
     * This method will re-read the json file if it has not yet been read, or if it has changed since we last read it.
     */
    private synchronized Map<String, JsonObject> getAssetMap() throws IOException {
        return assetMapCache.get(this::readAssetMap);
    }

    private Map<String, JsonObject> readAssetMap() throws IOException {
        HashMap<String, JsonObject> result = new HashMap<>();
        idCounter = new AtomicInteger(1);
        try (JsonReader reader = Json.createReader(new FileInputStream(file))) {
            JsonArray assetList = reader.readArray();
            for (JsonValue val : assetList) {
                String id = Integer.toString(idCounter.getAndIncrement());
                if (val.getValueType() == ValueType.OBJECT) {
                    result.put(id, (JsonObject) val);
                }
            }
        }
        return result;
    }

    @Override
    public Asset getAsset(String assetId) throws IOException, BadVersionException, RequestFailureException {
        JsonObject assetJson = getAssetMap().get(assetId);
        if (assetJson == null) {
            throw new RequestFailureException(404, "Asset does not exist", file.toURI().toURL(), "Asset does not exist");
        }
        Asset asset = DataModelSerializer.deserializeObject(assetJson, Asset.class, Verification.VERIFY);
        asset.set_id(assetId);
        addWlpInformation(asset);
        return asset;
    }

    @Override
    public Collection<Asset> getAllAssets() throws IOException {
        // Fetch via the cache and then make a copy before returning
        // to prevent a caller from modifying the cached data
        return CopyUtils.copyCollection(allAssetsCache.get(this::doGetAllAssets), Asset::new);
    }

    public Collection<Asset> doGetAllAssets() throws IOException {
        ArrayList<Asset> result = new ArrayList<Asset>();

        for (Entry<String, JsonObject> entry : getAssetMap().entrySet()) {
            if (entry.getValue() != null) {
                try {
                    Asset asset = DataModelSerializer.deserializeObject(entry.getValue(), Asset.class, Verification.VERIFY);
                    asset.set_id(entry.getKey());
                    addWlpInformation(asset);
                    result.add(asset);
                } catch (BadVersionException e) {
                    continue; // Skip anything invalid when returning all assets
                }
            }
        }

        return result;
    }

    @Override
    public InputStream getAttachment(Asset asset, Attachment attachment) throws IOException, BadVersionException, RequestFailureException {
        throw new UnsupportedOperationException("Single file repositories do not support attachments");
    }

    @Override
    public void checkRepositoryStatus() throws IOException, RequestFailureException {
        if (!file.canRead()) {
            throw new IOException("Cannot read repository file " + file.getAbsolutePath());
        }
    }

    @Override
    public void updateState(String assetId, StateAction action) throws IOException, RequestFailureException {
        // No-op, no states for file based repo
    }

    @Override
    public Asset addAsset(Asset asset) throws IOException, BadVersionException, RequestFailureException, SecurityException, ClientFailureException {
        if (asset.get_id() != null) {
            throw new ClientFailureException("Asset id is not null when adding a new asset", asset.get_id());
        }

        try {
            Map<String, JsonObject> assetMap = getAssetMap();
            JsonObject json = (JsonObject) DataModelSerializer.serializeAsJson(asset.createMinimalAssetForJSON());
            String id = Integer.toString(idCounter.getAndIncrement());
            assetMap.put(id, json);
            rewriteFile(assetMap);

            return getAsset(id);
        } catch (IllegalAccessException ex) {
            throw new IOException("Unable to create JSON for asset", ex);
        }
    }

    @Override
    public Asset updateAsset(Asset asset) throws IOException, BadVersionException, RequestFailureException, SecurityException, ClientFailureException {
        throw new UnsupportedOperationException("Single file repositories do not support updates");
    }

    @Override
    public Attachment addAttachment(String assetId, AttachmentSummary attSummary) throws IOException, BadVersionException, RequestFailureException, SecurityException {
        // Allow the add attachment call to succeed, though the attachment is not stored
        // This makes it easier to parse an asset and add it to a single file repository
        return null;
    }

    @Override
    public Attachment updateAttachment(String assetId, AttachmentSummary summary) throws IOException, BadVersionException, RequestFailureException, SecurityException {
        throw new UnsupportedOperationException("Single file repositories do not support attachments");
    }

    @Override
    public void deleteAttachment(String assetId, String attachmentId) throws IOException, RequestFailureException {
        throw new UnsupportedOperationException("Single file repositories do not support attachments");
    }

    @Override
    public void deleteAssetAndAttachments(String assetId) throws IOException, RequestFailureException {
        Map<String, JsonObject> assets = getAssetMap();
        if (assets.containsKey(assetId)) {
            assets.remove(assetId);
        }
        rewriteFile(assets);
    }

    /**
     * For historic reasons, when an asset is read back from the repository the wlpInformation and ATFI should always be present. This method does that.
     */
    private void addWlpInformation(Asset asset) {
        WlpInformation wlpInfo = asset.getWlpInformation();
        if (wlpInfo == null) {
            wlpInfo = new WlpInformation();
            asset.setWlpInformation(wlpInfo);
        }
        if (wlpInfo.getAppliesToFilterInfo() == null) {
            wlpInfo.setAppliesToFilterInfo(new ArrayList<AppliesToFilterInfo>());
        }
    }

    /**
     * Write the contents of {@link #assets} to {@link #file}.
     * <p>
     * This method ensures that the asset with id {@code n} is always written to the {@code n}th position in the file, using {@code null}s for padding if required.
     */
    private synchronized void rewriteFile(Map<String, JsonObject> assetMap) throws FileNotFoundException, IOException {
        JsonArrayBuilder jsonToStore = Json.createArrayBuilder();

        // Iterate through the assets in id order
        assetMap = assetMap == null ? Collections.emptyMap() : assetMap;
        for (int i = 1; i < idCounter.get(); i++) {
            JsonObject json = assetMap.get(Integer.toString(i));
            if (json == null) {
                jsonToStore.addNull();
            } else {
                jsonToStore.add(json);
            }
        }
        // Write the assets back to the file
        Map<String, Object> config = new HashMap<String, Object>();
        config.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory writerFactory = Json.createWriterFactory(config);
        try (FileOutputStream out = new FileOutputStream(file);
                        JsonWriter streamWriter = writerFactory.createWriter(out)) {
            streamWriter.write(jsonToStore.build());
        }
    }

}
