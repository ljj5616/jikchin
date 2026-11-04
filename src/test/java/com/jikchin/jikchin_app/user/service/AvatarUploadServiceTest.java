package com.jikchin.jikchin_app.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class AvatarUploadServiceTest {
    private final S3Client s3 = mock(S3Client.class);
    private final AvatarUploadService service = new AvatarUploadService(s3, "avatar-bucket");

    @Test
    void uploadsValidJpegUnderTheCurrentUser() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", output);
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", output.toByteArray());

        String key = service.upload(7L, file);

        assertThat(key).startsWith("avatars/users/7/").endsWith(".jpg");
        verify(s3).putObject(argThat((PutObjectRequest request) -> request.bucket().equals("avatar-bucket")
                && request.key().equals(key) && request.contentType().equals("image/jpeg")), any(RequestBody.class));
    }

    @Test
    void rejectsInvalidAndOversizedFilesWithoutUploading() {
        var invalid = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
        var oversized = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> service.upload(7L, invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upload(7L, oversized)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3);
    }
}
