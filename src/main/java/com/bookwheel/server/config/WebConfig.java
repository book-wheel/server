package com.bookwheel.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 실제 파일이 저장된 로컬 경로
        String uploadPath = Paths.get(System.getProperty("user.home"), "Desktop", "bookwheel", "profiles").toString();

        registry.addResourceHandler("/images/profiles/**") // 이 주소로 요청이 오면
                .addResourceLocations("file:///" + uploadPath + "/"); // 이 로컬 폴더에서 찾도록 설정
    }
}