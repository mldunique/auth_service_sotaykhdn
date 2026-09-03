package com.agribank.auth_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.documents")
public class DocumentConfigProperties {

    private String zipUrl = "/documents/tat-ca-tai-lieu.zip";
    private List<DocumentItem> items = new ArrayList<>();

    public String getZipUrl() {
        return zipUrl;
    }

    public void setZipUrl(String zipUrl) {
        this.zipUrl = zipUrl;
    }

    public List<DocumentItem> getItems() {
        return items;
    }

    public void setItems(List<DocumentItem> items) {
        this.items = items;
    }

    public static class DocumentItem {
        private String name;
        private String size;
        private String type; // "pdf" or "doc"
        private String url;

        public DocumentItem() {}

        public DocumentItem(String name, String size, String type, String url) {
            this.name = name;
            this.size = size;
            this.type = type;
            this.url = url;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
