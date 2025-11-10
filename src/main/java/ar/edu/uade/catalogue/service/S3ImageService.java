package ar.edu.uade.catalogue.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class S3ImageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

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
        URL url = new URL(sourceUrl);

        // Nombre del archivo
        String fileName = Path.of(url.getPath()).getFileName().toString();
        String key = "products/" + UUID.randomUUID() + "-" + fileName;

        // Crear archivo temporal
        Path tempFile = Files.createTempFile("download-", "-" + fileName);

        // Descargar completamente la imagen
        try (InputStream in = url.openStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Subir a S3
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .acl("public-read") // hace que sea accesible sin firma
                .build();

        s3Client.putObject(request, RequestBody.fromFile(tempFile));

        // Eliminar archivo temporal
        Files.deleteIfExists(tempFile);

        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }
}
