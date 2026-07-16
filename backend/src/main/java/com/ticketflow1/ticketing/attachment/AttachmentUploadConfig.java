package com.ticketflow1.ticketing.attachment;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
class AttachmentUploadConfig {

    @Bean
    MultipartConfigElement attachmentMultipartConfig(
            @Value("${app.attachments.max-size-bytes:104857600}") long maxSizeBytes) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        DataSize limit = DataSize.ofBytes(maxSizeBytes);
        factory.setMaxFileSize(limit);
        factory.setMaxRequestSize(limit);
        return factory.createMultipartConfig();
    }
}
