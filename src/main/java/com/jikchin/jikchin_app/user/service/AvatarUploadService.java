package com.jikchin.jikchin_app.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

@Service
public class AvatarUploadService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private final S3Client s3;
    private final String bucket;

    @Autowired
    public AvatarUploadService(@Value("${app.aws.region}") String region,
                               @Value("${app.aws.s3.bucket}") String bucket) {
        this(S3Client.builder().region(Region.of(region)).build(), bucket);
    }

    AvatarUploadService(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public String upload(Long userId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Image must be between 1 byte and 5 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("Invalid JPEG image");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    if (!"JPEG".equalsIgnoreCase(reader.getFormatName()) || reader.getWidth(0) > 4096 ||
                            reader.getHeight(0) > 4096 || reader.read(0) == null) {
                        throw new IllegalArgumentException("Invalid JPEG image");
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JPEG image", e);
        }
        String key = "avatars/users/" + userId + "/" + UUID.randomUUID() + ".jpg";
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/jpeg").build(),
                RequestBody.fromBytes(bytes));
        return key;
    }
}
