package ar.edu.uade.catalogue.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class S3ImageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    private static final Set<String> ALLOWED_EXT = Set.of("jpg","jpeg","png","webp");
    private static final Set<String> ALLOWED_CT = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";


    public S3ImageService(
            @Value("${AWS_ACCESS_KEY_ID}") String accessKey,
            @Value("${AWS_SECRET_ACCESS_KEY}") String secretKey) {

        this.bucketName = "d2-product-images-bucket";
        this.region = "sa-east-1";
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();
    }

    public String fromUrlToS3(String sourceUrl) throws IOException {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("URL de imagen vacío");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(sourceUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setInstanceFollowRedirects(true); // Seguir redirecciones explícitamente
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("El servidor devolvió un estado no válido: " + responseCode + " para la URL: " + sourceUrl);
            }

            String contentType = connection.getContentType();
            String mainContentType = (contentType != null) ? contentType.split(";")[0].trim().toLowerCase() : "";

            String fileName = Path.of(url.getPath()).getFileName().toString();
            String ext = extractExt(fileName);

            if (ext == null || ext.isBlank()) {
                if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(mainContentType)) ext = "jpg";
                else if (MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(mainContentType)) ext = "png";
                else if ("image/webp".equalsIgnoreCase(mainContentType)) ext = "webp";
            }

            if (ext == null || !ALLOWED_EXT.contains(ext.toLowerCase())) {
                throw new IllegalArgumentException("Formato de imagen no permitido o no reconocible para la URL: " + sourceUrl);
            }
            if (!ALLOWED_CT.contains(mainContentType)) {
                throw new IllegalArgumentException("Content-Type de imagen no permitido: " + contentType);
            }

            String safeName = sanitize(fileName, ext);
            String key = "products/" + UUID.randomUUID() + "-" + safeName;

            Path tempFile = Files.createTempFile("download-", "-" + safeName);
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .acl("public-read")
                    .contentType(toContentType(ext))
                    .build();

            s3Client.putObject(request, RequestBody.fromFile(tempFile));

            Files.deleteIfExists(tempFile);

            return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String extractExt(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = fileName.substring(dot + 1).toLowerCase();
        if (ext.contains("?")) ext = ext.substring(0, ext.indexOf('?'));
        if (ext.contains("#")) ext = ext.substring(0, ext.indexOf('#'));
        return ext;
    }

    private static String sanitize(String name, String ext) {
        String baseRaw = name == null ? "image" : name;
        int dot = baseRaw.lastIndexOf('.');
        String baseNoExt = dot > 0 ? baseRaw.substring(0, dot) : baseRaw;
        String base = baseNoExt.replaceAll("[^A-Za-z0-9_-]", "-");
        if (base.isBlank()) base = "image";
        int maxBaseLen = 80; // ajustable
        if (base.length() > maxBaseLen) {
            base = base.substring(0, maxBaseLen);
        }
        return base + "." + ext.toLowerCase();
    }

    private static String toContentType(String ext) {
        return switch (ext.toLowerCase()) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
