package com.junzhecai.controller;

import cn.hutool.core.util.StrUtil;
import com.junzhecai.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        String relPath = createNewFileName(image.getOriginalFilename());
        Path target = Paths.get(uploadDir, relPath.substring(1));
        try {
            image.transferTo(target.toFile());
            log.debug(relPath);
            return Result.ok(relPath);
        } catch (IOException e) {
            throw new RuntimeException("保存图片失败: " + target, e);
        }
    }

    private String createNewFileName(String originalFilename) {
        if (StrUtil.isBlank(originalFilename) || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("非法文件名: " + originalFilename);
        }
        String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase();

        String name = UUID.randomUUID().toString().replace("-", "");
        int hash = name.hashCode();
        /**
         * d1 = hash 的最低 4 位
         * d2 = hash 的第 5~8 位
         * 两两组合 = 256 个目录分片
         * & 0xF 负责"截取 4 位"，>> 4 负责"把要用的 4 位移到最低位"
         */
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;

        Path dir = Paths.get(uploadDir, "blogs", String.valueOf(d1), String.valueOf(d2));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("创建上传目录失败: " + dir, e);
        }

        return String.format("/blogs/%d/%d/%s.%s", d1, d2, name, suffix);
    }
}
