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
        URL url = new URL(sourceUrl);

        // Validar content-type remoto si es posible
        String contentType = null;
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("HEAD");
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            contentType = con.getContentType();
            con.disconnect();
        } catch (Exception ignore) {}

        // Nombre del archivo
        String fileName = Path.of(url.getPath()).getFileName().toString();
        String ext = extractExt(fileName);
        if (ext == null && contentType != null) {
            if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType)) ext = "jpg";
            else if (MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)) ext = "png";
            else if ("image/webp".equalsIgnoreCase(contentType)) ext = "webp";
        }
        if (ext == null) {
            throw new IllegalArgumentException("No se pudo determinar la extensión de la imagen para URL: " + sourceUrl);
        }
        if (!ALLOWED_EXT.contains(ext.toLowerCase())) {
            throw new IllegalArgumentException("Formato de imagen no permitido (solo jpg/jpeg/png/webp): " + ext);
        }
        if (contentType != null && !ALLOWED_CT.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Content-Type de imagen no permitido: " + contentType);
        }

        String safeName = sanitize(fileName, ext);
        String key = "products/" + UUID.randomUUID() + "-" + safeName;

        // Crear archivo temporal y descargar completamente la imagen
        Path tempFile = Files.createTempFile("download-", "-" + safeName);
        try (InputStream in = url.openStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Subir a S3
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .acl("public-read")
                .contentType(toContentType(ext))
                .build();

        s3Client.putObject(request, RequestBody.fromFile(tempFile));

        Files.deleteIfExists(tempFile);

        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }

    private static String extractExt(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.')
;        if (dot < 0) return null;
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
