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
import java.nio.file.Paths;
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
                        AwsBasicCredentials.create(accessKey,secretKey)
                ))
        .build();
    }

    // Aca estan los dos metodos. Dependiendo del manejo que elijamos borrar el otro metodo

    public String fromUrlToS3 (String sourceUrl) throws IOException {
        URL url = new URL(sourceUrl);

        // Agarro el nombre del archivo de la url
        String fileName = Paths.get(url.getPath()).getFileName().toString();
        String key = "products/" + UUID.randomUUID() + "-" + fileName;

        try (InputStream inputStream = url.openStream()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    // Hago la URL publica para que no tenga que estar firmada ni tenga tiempo de vida
                    .acl("public-read")
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, inputStream.available()));
        }

        return "https://" + bucketName + ".s3.amazonaws.com/" + key;
    }
}