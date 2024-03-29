package com.gft.manager.service.impl;


import com.gft.manager.dto.Response;
import com.gft.manager.exception.FileStorageException;
import com.gft.manager.exception.MyFileNotFoundException;
import com.gft.manager.properties.FileStorageProperties;
import com.gft.manager.util.ResponseBuilder;
import com.gft.manager.validation.validator.ExcelFileValidator;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;


@Service
@Log4j2
public class FileStorageService {

    private final Path fileStorageLocation;

    @Autowired
    public FileStorageService(FileStorageProperties fileStorageProperties) {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            log.info("create the file directory first  location not found ");
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }


    public Response storeFile(MultipartFile file) {
        // Normalize file name
        ExcelFileValidator validator = new ExcelFileValidator();

        String fileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        if (!validator.validate(fileName)){
           return ResponseBuilder.getFailureResponse(HttpStatus.BAD_REQUEST,
                   "file is not support only allow[“.csv”,“.xlsx”]");
        }
        String finalReplace = replaceFileName(file);

        try {
            // Check if the file's name contains invalid characters
            if(fileName.contains("..")) {
                return ResponseBuilder.getFailureResponse(HttpStatus.BAD_REQUEST,"Sorry! Filename contains invalid path sequence "+fileName);
            }

            // Copy file to the target location (Replacing existing file with the same name)
            Path targetLocation = this.fileStorageLocation.resolve(finalReplace);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return ResponseBuilder.getSuccessResponse(HttpStatus.CREATED,"file save successfully",targetLocation);
        } catch (IOException ex) {
            return ResponseBuilder.getFailureResponse(HttpStatus.BAD_REQUEST,"Could not store file " + fileName + ". Please try again!");
//            throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    private String replaceFileName(MultipartFile file) {
        String[] split = Objects.requireNonNull(file.getOriginalFilename()).split("\\.");
        return split[0]= UUID.randomUUID() +"."+split[1];
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if(resource.exists()) {
                return resource;
            } else {
                throw new MyFileNotFoundException("File not found " + fileName);
            }
        } catch (MalformedURLException ex) {
            throw new MyFileNotFoundException("File not found " + fileName, ex);
        }
    }
}
