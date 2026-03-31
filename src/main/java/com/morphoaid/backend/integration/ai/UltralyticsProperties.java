package com.morphoaid.backend.integration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ultralytics")
public class UltralyticsProperties {

    private String baseUrl = "https://predict.ultralytics.com";
    private String modelUrl = "https://hub.ultralytics.com/models/BWEuas2HvT4UyVn6IKVz";
    private Integer imgsz = 640;
    private Double conf = 0.25;
    private Double iou = 0.45;
    private String apiKey;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelUrl() {
        return modelUrl;
    }

    public void setModelUrl(String modelUrl) {
        this.modelUrl = modelUrl;
    }

    public Integer getImgsz() {
        return imgsz;
    }

    public void setImgsz(Integer imgsz) {
        this.imgsz = imgsz;
    }

    public Double getConf() {
        return conf;
    }

    public void setConf(Double conf) {
        this.conf = conf;
    }

    public Double getIou() {
        return iou;
    }

    public void setIou(Double iou) {
        this.iou = iou;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
