/*
 *     Copyright 2017 EntIT Software LLC, a Micro Focus company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */


package com.microfocus.mqm.atrf.octane.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microfocus.mqm.atrf.core.rest.HTTPUtils;
import com.microfocus.mqm.atrf.core.rest.Response;
import com.microfocus.mqm.atrf.core.rest.RestConnector;
import com.microfocus.mqm.atrf.core.rest.SupportRelogin;
import com.microfocus.mqm.atrf.octane.core.OctaneEntity;
import com.microfocus.mqm.atrf.octane.core.OctaneEntityCollection;
import com.microfocus.mqm.atrf.octane.core.OctaneEntityDescriptor;
import com.microfocus.mqm.atrf.octane.core.OctaneTestResultOutput;
import com.microfocus.mqm.atrf.octane.entities.*;
import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by berkovir on 21/11/2016.
 */
public class OctaneEntityService implements SupportRelogin {

    private RestConnector restConnector;
    private long sharedSpaceId;
    private long workspaceId;
    private OctaneAuthenticationPojo authData;

    Map<String, OctaneEntityDescriptor> typesMap = new HashMap<>();

    public OctaneEntityService(RestConnector restConnector) {
        this.restConnector = restConnector;

        registerTypes();
    }

    private void registerTypes() {
        typesMap.put(Test.TYPE, new TestDescriptor());
        typesMap.put(ListNode.TYPE, new ListNodeDescriptor());
        typesMap.put(WorkspaceUser.TYPE, new WorkspaceUserDescriptor());
        typesMap.put(TestVersion.TYPE, new TestVersionDescriptor());
        typesMap.put(Phase.TYPE, new PhaseDescriptor());
        typesMap.put(Release.TYPE, new ReleaseDescriptor());
        typesMap.put(Sprint.TYPE, new SprintDescriptor());
        typesMap.put(Workspace.TYPE, new WorkspaceDescriptor());
    }


    public boolean login(String user, String password) {
        authData = new OctaneAuthenticationPojo();
        authData.setUser(user);
        authData.setPassword(password);
        authData.setEnable_csrf(true);
        boolean result = loginInternal();
        if (result) {
            restConnector.setSupportRelogin(this);
        }

        return result;
    }

    @Override
    public boolean relogin() {
        return loginInternal();
    }

    private boolean loginInternal() {
        boolean ret = false;
        restConnector.clearAll();
        ObjectMapper mapper = new ObjectMapper();
        String jsonString = null;
        try {
            jsonString = mapper.writeValueAsString(authData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Fail in generating json for login data : " + e.getMessage());
        }

        //Get LWSSO COOKIE
        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPUtils.HEADER_CONTENT_TYPE, HTTPUtils.HEADER_APPLICATION_JSON);
        Response authResponse = restConnector.httpPost(OctaneRestConstants.AUTHENTICATION_URL, jsonString, headers);
        if (authResponse.getStatusCode() == HttpStatus.SC_OK) {
            ret = true;
        }

        return ret;
    }


    public void setWorkspaceId(long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public long getSharedSpaceId() {
        return sharedSpaceId;
    }

    public void setSharedSpaceId(long sharedSpaceId) {
        this.sharedSpaceId = sharedSpaceId;
    }

    public long getWorkspaceId() {
        return workspaceId;
    }

    public OctaneEntityCollection getEntities(String type, OctaneQueryBuilder queryBuilder) {
        String entityCollectionUrl = null;
        OctaneEntityDescriptor descriptor = typesMap.get(type);
        if (descriptor.getContext().equals(OctaneEntityDescriptor.Context.Workspace)) {
            entityCollectionUrl = String.format(OctaneRestConstants.PUBLIC_API_WORKSPACE_LEVEL_ENTITIES, getSharedSpaceId(), getWorkspaceId(), descriptor.getCollectionName());
        } else {
            entityCollectionUrl = String.format(OctaneRestConstants.PUBLIC_API_SHAREDSPACE_LEVEL_ENTITIES, getSharedSpaceId(), descriptor.getCollectionName());
        }
        String queryString = queryBuilder.build();

        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPUtils.HEADER_ACCEPT, HTTPUtils.HEADER_APPLICATION_JSON);
        headers.put(OctaneRestConstants.CLIENTTYPE_HEADER, OctaneRestConstants.CLIENTTYPE_INTERNAL);

        String entitiesCollectionStr = restConnector.httpGet(entityCollectionUrl, Arrays.asList(queryString), headers).getResponseData();
        JSONObject jsonObj = new JSONObject(entitiesCollectionStr);
        OctaneEntityCollection col = parseCollection(jsonObj);
        return col;
    }

    public OctaneTestResultOutput postTestResults(String data) {
        String entityCollectionUrl = String.format(OctaneRestConstants.PUBLIC_API_WORKSPACE_LEVEL_ENTITIES, getSharedSpaceId(), getWorkspaceId(), "test-results");

        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPUtils.HEADER_ACCEPT, HTTPUtils.HEADER_APPLICATION_JSON);
        headers.put(HTTPUtils.HEADER_CONTENT_TYPE, HTTPUtils.HEADER_APPLICATION_XML);

        String responseStr = restConnector.httpPost(entityCollectionUrl, data, headers).getResponseData();
        OctaneTestResultOutput result = parseTestResultOutput(responseStr);
        return result;
    }

    public OctaneTestResultOutput getTestResultStatus(OctaneTestResultOutput output) {
        String entityCollectionUrl = String.format(OctaneRestConstants.PUBLIC_API_WORKSPACE_LEVEL_ENTITIES, getSharedSpaceId(), getWorkspaceId(), "test-results") + "/" + output.getId();
        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPUtils.HEADER_ACCEPT, HTTPUtils.HEADER_APPLICATION_JSON);


        String responseStr = restConnector.httpGet(entityCollectionUrl, null, headers).getResponseData();
        OctaneTestResultOutput result = parseTestResultOutput(responseStr);
        return result;
    }

    private OctaneTestResultOutput parseTestResultOutput(String responseStr) {
        JSONObject jsonObj = new JSONObject(responseStr);
        OctaneTestResultOutput result = new OctaneTestResultOutput();
        result.put(OctaneTestResultOutput.FIELD_ID, jsonObj.get(OctaneTestResultOutput.FIELD_ID));
        result.put(OctaneTestResultOutput.FIELD_STATUS, jsonObj.get(OctaneTestResultOutput.FIELD_STATUS));

        return result;
    }

    private OctaneEntityCollection parseCollection(JSONObject jsonObj) {
        OctaneEntityCollection coll = new OctaneEntityCollection();

        int total = jsonObj.getInt("total_count");
        coll.setTotalCount(total);

        if (jsonObj.has("exceeds_total_count")) {
            boolean exceedsTotalCount = jsonObj.getBoolean("exceeds_total_count");
            coll.setExceedsTotalCount(exceedsTotalCount);
        }

        JSONArray entitiesJArr = jsonObj.getJSONArray("data");
        for (int i = 0; i < entitiesJArr.length(); i++) {

            JSONObject entObj = entitiesJArr.getJSONObject(i);
            OctaneEntity entity = parseEntity(entObj);

            coll.getData().add(entity);
        }

        return coll;
    }

    private OctaneEntity parseEntity(JSONObject entObj) {

        String type = entObj.getString("type");

        OctaneEntity entity = createEntity(type);
        for (String key : entObj.keySet()) {
            Object value = entObj.get(key);
            if (value instanceof JSONObject) {
                JSONObject jObj = (JSONObject) value;
                if (jObj.has("type")) {
                    OctaneEntity valueEntity = parseEntity(jObj);
                    value = valueEntity;
                } else if (jObj.has("total_count")) {
                    OctaneEntityCollection coll = parseCollection(jObj);
                    value = coll;
                } else {
                    value = jObj.toString();
                }
            } else if (JSONObject.NULL.equals(value)) {
                value = null;
            }
            entity.put(key, value);
        }
        return entity;
    }

    private OctaneEntity createEntity(String type) {
        OctaneEntityDescriptor descriptor = typesMap.get(type);
        if (descriptor == null) {
            //return new OctaneEntity(type);
            throw new RuntimeException("Unregistered type " + type);
        }
        OctaneEntity entity = null;
        try {
            entity = descriptor.getEntityClass().getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create entity of type " + type, e);
        }

        return entity;
    }

}
