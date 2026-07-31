package com.example.payandscan.payandscan.model;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "users")
public class User {
    private String userId;
    private String email;
    private String name;
    private boolean isVerified;
}
