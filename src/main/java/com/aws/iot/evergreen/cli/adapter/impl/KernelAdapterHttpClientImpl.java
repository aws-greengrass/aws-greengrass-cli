package com.aws.iot.evergreen.cli.adapter.impl;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import com.aws.iot.evergreen.cli.model.DeploymentDocument;
import com.aws.iot.evergreen.cli.model.DeploymentPackageConfiguration;
import com.aws.iot.evergreen.cli.util.FileUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class KernelAdapterHttpClientImpl implements KernelAdapter {

    private static final String HTTP_ENDPOINT = "http://localhost:1441/";

    private static final ObjectMapper SERIALZIER = new ObjectMapper();

    @Override
    public Map<String, String> getConfigs(Set<String> configPaths) {
        List<String> pathList = new ArrayList<>(configPaths);
        String query = String.join("&", pathList);

        URI uri;
        try {
            uri = new URI(HTTP_ENDPOINT + "get.txt?" + query);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to construct config get uri");
        }

        String result = httpGet(uri);
        if (result != null) {
            String[] values = result.split("\\r?\\n");
            if (values.length != pathList.size()) {
                throw new RuntimeException("Get config error");
            }
            Map<String, String> configValueMap = new LinkedHashMap<>();
            for (int i = 0; i < pathList.size(); i++) {
                configValueMap.put(pathList.get(i), values[i]);
            }

            return configValueMap;
        }

        return Collections.emptyMap();
    }

    @Override
    public void setConfigs(Map<String, String> configs) {
        URI uri;
        try {
            URIBuilder uriBuilder = new URIBuilder(HTTP_ENDPOINT + "set.txt");
            configs.forEach(uriBuilder::setParameter);
            uri = uriBuilder.build();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to construct config set uri");
        }

        httpGet(uri);
    }

    @Override
    public String healthPing() {
        URI uri;
        try {
            uri = new URI(HTTP_ENDPOINT + "health.json");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to construct health check uri");
        }
        return httpGet(uri);
    }

    @Override
    public Map<String, Map<String, String>> getServicesStatus(Set<String> serviceNames) {
        return deserializeServicesStatus(httpGet(buildServiceOperationURI(serviceNames)));
    }

    @Override
    public Map<String, Map<String, String>> restartServices(Set<String> serviceNames) {
        return deserializeServicesStatus(httpPut(buildServiceOperationURI(serviceNames), null));
    }

    @Override
    public Map<String, Map<String, String>> stopServices(Set<String> serviceNames) {
        return deserializeServicesStatus(httpDelete(buildServiceOperationURI(serviceNames)));
    }

    @Override
    public void localOverride(LocalOverrideRequest localOverrideRequest) {
        URI uri = null;
        StringEntity entity = null;
        try {
            copyRecipeAndArtifactToPackageStore(localOverrideRequest.getRecipeDir(), localOverrideRequest.getArtifactDir());
            uri = new URI(HTTP_ENDPOINT + "deploy");
            entity = new StringEntity(SERIALZIER.writeValueAsString(localOverrideRequest));
        } catch (Exception e) {
            e.printStackTrace();
        }

        HttpPost httpPost = new HttpPost(uri);
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        sendHttpRequest(httpPost);
    }

    private void copyRecipeAndArtifactToPackageStore(String recipeDir,
                                                     String artifactDir) throws IOException {
        String packageStorePath = getPackageStorePath();
        System.out.println("Copying artifacts to package store: " + packageStorePath);

        if (recipeDir != null) {
            FileUtils.copyFolderRecursively(Paths.get(recipeDir), Paths.get(packageStorePath, "recipes"));
        }

        if (artifactDir != null) {
            FileUtils.copyFolderRecursively(Paths.get(artifactDir), Paths.get(packageStorePath, "artifacts"));
        }
    }


    private String getPackageStorePath() {
        URI uri;
        try {
            uri = new URI(HTTP_ENDPOINT + "deploy.json");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to construct config get uri");
        }

        return httpGet(uri);
    }

    private URI buildServiceOperationURI(Set<String> serviceNames) {
        List<String> nameList = new ArrayList<>(serviceNames);
        String query = String.join("&", nameList);

        try {
            return new URI(HTTP_ENDPOINT + "services?" + query);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to construct service query uri");
        }
    }

    private String sendHttpRequest(HttpRequestBase requestBase) {
        requestBase.addHeader(HttpHeaders.USER_AGENT, "evergreen-cli");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();

             CloseableHttpResponse response = httpClient.execute(requestBase)) {

            // Get HttpResponse Status
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 500) {
                throw new RuntimeException("Service side error");
            } else if (statusCode >= 400) {
                throw new RuntimeException("Client side error");
            }

            Optional<HttpEntity> entity = Optional.ofNullable(response.getEntity());

            return entity.map(e -> {
                String result;
                try {
                    result = EntityUtils.toString(e);
                } catch (IOException ex) {
                    throw new RuntimeException("Failed to convert http entity to string");
                }
                return result;
            }).orElse(null);

        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to send http %s request, uri %s", requestBase.getMethod(),
                    requestBase.getURI()));
        }
    }

    private String httpGet(URI uri) {
        return sendHttpRequest(new HttpGet(uri));
    }

    private String httpDelete(URI uri) {
        return sendHttpRequest(new HttpDelete(uri));
    }

    private String httpPut(URI uri, Map<String, String> paramMap) {
        HttpPut request = new HttpPut(uri);

        if (paramMap != null && paramMap.size() > 0) {
            List<NameValuePair> params = new ArrayList<>();
            paramMap.forEach((k, v) -> {
                params.add(new BasicNameValuePair(k, v));
            });
            try {
                request.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Failed to encode http put parameters");
            }
        }
        return sendHttpRequest(request);

    }

    private Map<String, Map<String, String>> deserializeServicesStatus(String json) {
        try {
            return SERIALZIER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize service status json " + json);
        }
    }
}
