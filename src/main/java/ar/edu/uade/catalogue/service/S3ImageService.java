package ar.edu.uade.catalogue.service;

import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
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

    public S3ImageService(
            @Value("${cloud.aws.region.static}") String region,
            @Value("${cloud.aws.s3.bucket-name}") String bucketName,
            @Value("${AWS_ACCESS_KEY_ID}") String accessKey,
            @Value("${AWS_SECRET_ACCESS_KEY}") String secretKey) {

        this.bucketName = bucketName;
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

    public String uploadImage(MultipartFile file){
        if(file.isEmpty()){
            throw new IllegalArgumentException("Archivo vacio");
        }

        // Generando nombre unico para evitar colisiones
        String originalName = file.getOriginalFilename();
        String extension = "";

        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        // construyo la key de la imagen 
        String key = "products/" + UUID.randomUUID() + extension;

        // Request de subida
        PutObjectRequest request = new PutObjectRequest().builder()
           .bucket(bucketName)
           .key(key)
           .acl(ObjectCannedACL.PUBLIC_READ) // Cualquiera con la URL ve la imagen
           .build();

        
           // Subir el archivo
        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return "https://" + bucketName + ".s3.amazonaws.com/" + key;
    }
}