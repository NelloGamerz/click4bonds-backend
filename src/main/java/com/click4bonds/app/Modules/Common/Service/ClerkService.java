// package com.click4bonds.app.Modules.Common.Service;

// import java.util.Map;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;
// import org.springframework.stereotype.Service;
// import org.springframework.web.reactive.function.client.WebClient;

// import com.click4bonds.app.Modules.User.Enums.UserRole;

// import lombok.RequiredArgsConstructor;

// @Service
// @RequiredArgsConstructor
// public class ClerkService {

//     private final WebClient.Builder webClientBuilder;

//     @Value("${clerk.secret-key}")
//     private String clerkSecretKey;

//     public void updateUserRole(
//             String clerkUserId,
//             UserRole role
//     ) {

//         webClientBuilder
//                 .build()
//                 .patch()
//                 .uri(
//                         "https://api.clerk.com/v1/users/{userId}/metadata",
//                         clerkUserId
//                 )
//                 .header(
//                         HttpHeaders.AUTHORIZATION,
//                         "Bearer " + clerkSecretKey
//                 )
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(
//                         Map.of(
//                                 "public_metadata",
//                                 Map.of(
//                                         "role",
//                                         role.name().toLowerCase()
//                                 )
//                         )
//                 )
//                 .retrieve()
//                 .toBodilessEntity()
//                 .block();
//     }
// }


package com.click4bonds.app.Modules.Common.Service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.click4bonds.app.Modules.User.Enums.UserRole;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClerkService {

    private final RestClient restClient;

    @Value("${clerk.secret-key}")
    private String clerkSecretKey;

    public void updateUserRole(
            String clerkUserId,
            UserRole role
    ) {

        restClient
                .patch()
                .uri(
                        "https://api.clerk.com/v1/users/{userId}/metadata",
                        clerkUserId
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + clerkSecretKey
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        Map.of(
                                "public_metadata",
                                Map.of(
                                        "role",
                                        role.name().toLowerCase()
                                )
                        )
                )
                .retrieve()
                .toBodilessEntity();
    }
}